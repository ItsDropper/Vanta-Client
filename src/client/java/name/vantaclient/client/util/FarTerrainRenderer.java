package name.vantaclient.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import name.vantaclient.client.feature.FarViewFeature;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Singleplayer-only far terrain renderer.
 *
 * The integrated server's generator supplies base terrain heights. A bounded,
 * single-worker pipeline turns those immutable height samples into coarse meshes;
 * rendering consumes only completed snapshots on the render thread. It never
 * requests millions of full chunks or reads live chunk state from worker threads.
 *
 * This is a base-terrain LOD: structures, foliage, caves, and post-generation
 * block edits are intentionally not represented outside vanilla view distance.
 */
public final class FarTerrainRenderer {
    private static final int MAX_FAR_DISTANCE = 2048;
    private static final int ROOT_TILE_SIZE_CHUNKS = 2048;
    private static final int MIN_TILE_SIZE_CHUNKS = 2;
    private static final int ADAPTIVE_REFRESH_TICKS = 40;
    private static final int MAX_TILES_TO_SCHEDULE_PER_TICK = 6;
    private static final int MAX_QUEUED_TILE_JOBS = 96;
    private static final int MAX_DRAW_QUADS = 96_000;
    private static final int MAX_DESIRED_TILES = 4096;
    // Keep the CPU-side LOD cache bounded even at 2,048 chunks and ULTRA quality.
    private static final long MAX_MESH_CACHE_BYTES = 48L * 1024L * 1024L;
    private static final int SKIRT_MIN_DEPTH = 24;

    private static final Set<WorkKey> PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<TileKey> FAILED = ConcurrentHashMap.newKeySet();
    private static final Set<TileKey> CACHE_EVICTED = ConcurrentHashMap.newKeySet();
    private static volatile Set<TileKey> DESIRED = Set.of();
    private static final ConcurrentLinkedQueue<TileMesh> COMPLETED = new ConcurrentLinkedQueue<>();
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_TILE_JOBS),
            runnable -> {
                Thread thread = new Thread(runnable, "Vanta-FarTerrain");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    private static final Map<TileKey, TileMesh> MESHES = new HashMap<>();
    private static long cachedMeshBytes;
    private static List<TileKey> orderedTiles = List.of();

    private static ServerLevel activeLevel;
    private static volatile long revision;
    private static int ticksUntilAdaptiveUpdate;
    private static int effectiveFarDistance = 512;
    private static int lastSampleSpacing = -1;
    private static int lastDetailDistance = -1;
    private static int lastConfiguredFarDistance = -1;
    private static int lastPlayerChunkX = Integer.MIN_VALUE;
    private static int lastPlayerChunkZ = Integer.MIN_VALUE;
    private static volatile String lastWorkerError = "";
    private static double averageMeshSubmitMs;
    private static volatile double lastTilePlanMs;
    private static volatile double averageTileGenerationMs;
    private static volatile double lastTileGenerationMs;
    private static volatile long tilePlanBuildCount;
    private static volatile long tileGenerationSamples;
    private static volatile int lastDrawnQuads;
    private static volatile int lastSubmittedMeshes;
    private static int adaptiveDrawBudget = 36_000;
    private static int drawBudgetAdjustmentFrames;
    private static volatile long workerBackoffNanos;
    private static volatile int lastPrunedQueuedJobs;
    private static volatile long totalPrunedQueuedJobs;

    private FarTerrainRenderer() {}

    public static void register() {
        FarTerrainRenderType.initialize();
        WorldRenderEvents.BEFORE_ENTITIES.register(FarTerrainRenderer::render);
    }

    public static void tick(Minecraft client) {
        if (!FarViewFeature.INSTANCE.isEnabled() || client.player == null || client.level == null
                || !client.hasSingleplayerServer()) {
            clearWorld();
            return;
        }

        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || !server.isRunning()) {
            clearWorld();
            return;
        }

        ServerLevel serverLevel = server.getLevel(client.level.dimension());
        if (serverLevel == null) {
            clearWorld();
            return;
        }

        if (activeLevel != serverLevel) {
            clearWorld();
            activeLevel = serverLevel;
            effectiveFarDistance = FarViewFeature.getFarViewDistance();
        }

        updateAdaptiveDistance(client);
        updateWorkerBackoff(client);
        drainCompletedMeshes();

        int playerChunkX = client.player.chunkPosition().x;
        int playerChunkZ = client.player.chunkPosition().z;
        int sampleSpacingChunks = FarViewFeature.getSampleSpacingChunks();
        int detailDistance = Math.max(2, FarViewController.getEffectiveRenderDistance());
        int configuredFarDistance = effectiveFarDistance;

        if (FarTerrainLodMath.shouldRebuildPlan(!orderedTiles.isEmpty(),
                lastPlayerChunkX, lastPlayerChunkZ, playerChunkX, playerChunkZ,
                lastSampleSpacing, sampleSpacingChunks,
                lastDetailDistance, detailDistance,
                lastConfiguredFarDistance, configuredFarDistance)) {
            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkZ = playerChunkZ;
            lastSampleSpacing = sampleSpacingChunks;
            lastDetailDistance = detailDistance;
            lastConfiguredFarDistance = configuredFarDistance;
            rebuildDesiredTiles(playerChunkX, playerChunkZ, detailDistance,
                    configuredFarDistance, sampleSpacingChunks);
        }

        scheduleMissingTiles(serverLevel);
    }

    private static void updateAdaptiveDistance(Minecraft client) {
        int target = FarViewFeature.getFarViewDistance();
        if (!FarViewFeature.isAdaptiveEnabled()) {
            effectiveFarDistance = target;
            ticksUntilAdaptiveUpdate = 0;
            return;
        }

        if (ticksUntilAdaptiveUpdate-- > 0) return;
        ticksUntilAdaptiveUpdate = ADAPTIVE_REFRESH_TICKS;
        effectiveFarDistance = Math.min(effectiveFarDistance <= 0 ? target : effectiveFarDistance, target);

        double renderMs = ClientPerformanceMetrics.getAverageWorldRenderMs();
        double tickMs = ClientPerformanceMetrics.getAverageClientTickMs();
        if (renderMs <= 0.0 && tickMs <= 0.0) return;

        boolean overloaded = renderMs > 22.0 || tickMs > 28.0 || client.getFps() < 45;
        boolean hasHeadroom = renderMs > 0.0 && renderMs < 12.0
                && (tickMs <= 0.0 || tickMs < 12.0) && client.getFps() >= 100;

        if (overloaded) {
            effectiveFarDistance = Math.max(Math.min(64, target), effectiveFarDistance - 64);
        } else if (hasHeadroom && effectiveFarDistance < target) {
            effectiveFarDistance = Math.min(target, effectiveFarDistance + 32);
        }
    }

    private static void updateWorkerBackoff(Minecraft client) {
        if (!FarViewFeature.isAdaptiveEnabled()) {
            workerBackoffNanos = 0L;
            return;
        }
        int backoffMillis = FarTerrainLodMath.workerBackoffMillis(
                ClientPerformanceMetrics.getAverageWorldRenderMs(),
                ClientPerformanceMetrics.getAverageClientTickMs(),
                client.getFps());
        workerBackoffNanos = TimeUnit.MILLISECONDS.toNanos(backoffMillis);
    }

    private static void rebuildDesiredTiles(int playerChunkX, int playerChunkZ,
                                             int detailDistance, int farDistance, int sampleSpacingChunks) {
        long planStartedNanos = System.nanoTime();
        Set<TileKey> nextDesired = new HashSet<>();
        int rootMinX = Math.floorDiv(playerChunkX - farDistance, ROOT_TILE_SIZE_CHUNKS)
                * ROOT_TILE_SIZE_CHUNKS;
        int rootMaxX = Math.floorDiv(playerChunkX + farDistance, ROOT_TILE_SIZE_CHUNKS)
                * ROOT_TILE_SIZE_CHUNKS;
        int rootMinZ = Math.floorDiv(playerChunkZ - farDistance, ROOT_TILE_SIZE_CHUNKS)
                * ROOT_TILE_SIZE_CHUNKS;
        int rootMaxZ = Math.floorDiv(playerChunkZ + farDistance, ROOT_TILE_SIZE_CHUNKS)
                * ROOT_TILE_SIZE_CHUNKS;

        for (int rootX = rootMinX; rootX <= rootMaxX; rootX += ROOT_TILE_SIZE_CHUNKS) {
            for (int rootZ = rootMinZ; rootZ <= rootMaxZ; rootZ += ROOT_TILE_SIZE_CHUNKS) {
                collectLeaves(rootX, rootZ, ROOT_TILE_SIZE_CHUNKS,
                        playerChunkX, playerChunkZ, detailDistance, farDistance,
                        sampleSpacingChunks, nextDesired);
            }
        }

        List<TileKey> sorted = new ArrayList<>(nextDesired);
        sorted.sort(Comparator.comparingDouble(key -> distanceSquaredToCenter(
                key, playerChunkX + 0.5, playerChunkZ + 0.5)));
        if (sorted.size() > MAX_DESIRED_TILES) {
            sorted = new ArrayList<>(sorted.subList(0, MAX_DESIRED_TILES));
        }
        nextDesired.clear();
        nextDesired.addAll(sorted);

        DESIRED = Set.copyOf(nextDesired);
        FAILED.removeIf(key -> !nextDesired.contains(key));
        // Re-admit an intentionally evicted tile only after it moves into the
        // inner 65% of the active radius. This prevents later movement-driven
        // plan rebuilds from regenerating distant tiles that the memory cap evicts.
        double reentryDistance = farDistance * 0.65;
        double reentryDistanceSquared = reentryDistance * reentryDistance;
        CACHE_EVICTED.removeIf(key -> !nextDesired.contains(key)
                || distanceSquaredToCenter(key, playerChunkX, playerChunkZ)
                <= reentryDistanceSquared);
        MESHES.keySet().removeIf(key -> !nextDesired.contains(key));
        recalculateMeshCacheBytes();
        orderedTiles = List.copyOf(sorted);
        lastPrunedQueuedJobs = pruneObsoleteQueuedJobs();
        totalPrunedQueuedJobs += lastPrunedQueuedJobs;
        lastTilePlanMs = (System.nanoTime() - planStartedNanos) / 1_000_000.0;
        tilePlanBuildCount++;
    }

    private static void collectLeaves(int minChunkX, int minChunkZ, int size,
                                      int playerChunkX, int playerChunkZ,
                                      int detailDistance, int farDistance, int sampleSpacingChunks,
                                      Set<TileKey> output) {
        double nearestDistanceSquared = FarTerrainLodMath.distanceToRectangleSquared(
                playerChunkX + 0.5, playerChunkZ + 0.5,
                minChunkX, minChunkZ, minChunkX + size, minChunkZ + size);
        if (nearestDistanceSquared > (double) farDistance * farDistance) return;

        double farthestDistanceSquared = FarTerrainLodMath.farthestCornerDistanceSquared(
                playerChunkX + 0.5, playerChunkZ + 0.5,
                minChunkX, minChunkZ, minChunkX + size, minChunkZ + size);
        double detailThreshold = Math.max(1, detailDistance - 1);
        if (farthestDistanceSquared < detailThreshold * detailThreshold) return;

        double centerDistanceSquared = distanceSquaredToCenter(
                minChunkX, minChunkZ, size, playerChunkX + 0.5, playerChunkZ + 0.5);
        int desiredSize = FarTerrainLodMath.desiredTileSizeForDistanceSquared(centerDistanceSquared);

        if (size > desiredSize && size > MIN_TILE_SIZE_CHUNKS) {
            int half = size / 2;
            collectLeaves(minChunkX, minChunkZ, half, playerChunkX, playerChunkZ,
                    detailDistance, farDistance, sampleSpacingChunks, output);
            collectLeaves(minChunkX + half, minChunkZ, half, playerChunkX, playerChunkZ,
                    detailDistance, farDistance, sampleSpacingChunks, output);
            collectLeaves(minChunkX, minChunkZ + half, half, playerChunkX, playerChunkZ,
                    detailDistance, farDistance, sampleSpacingChunks, output);
            collectLeaves(minChunkX + half, minChunkZ + half, half, playerChunkX, playerChunkZ,
                    detailDistance, farDistance, sampleSpacingChunks, output);
            return;
        }

        int tileResolution = resolutionForDistance(size, sampleSpacingChunks);
        output.add(new TileKey(minChunkX, minChunkZ, size, tileResolution));
    }

    private static int resolutionForDistance(int tileSizeChunks, int targetSpacingChunks) {
        int requested = (int) Math.ceil(tileSizeChunks / (double) targetSpacingChunks);
        return Math.max(4, Math.min(64, requested));
    }

    private static void scheduleMissingTiles(ServerLevel level) {
        int scheduled = 0;
        ChunkGenerator generator = null;
        RandomState randomState = null;
        BiomeSource biomeSource = null;
        Climate.Sampler climateSampler = null;
        LevelHeightAccessor heightAccessor = null;
        int seaLevel = 0;
        boolean waterSurfaceEnabled = false;
        boolean generationContextReady = false;

        for (TileKey key : orderedTiles) {
            if (scheduled >= MAX_TILES_TO_SCHEDULE_PER_TICK) break;
            if (MESHES.containsKey(key) || FAILED.contains(key) || CACHE_EVICTED.contains(key)) continue;
            WorkKey workKey = new WorkKey(key, revision);
            if (PENDING.contains(workKey)) continue;

            // Avoid touching generator/heightmap context on idle ticks where every
            // desired tile is already cached, failed, evicted, or currently pending.
            if (!generationContextReady) {
                generator = level.getChunkSource().getGenerator();
                randomState = level.getChunkSource().randomState();
                biomeSource = generator.getBiomeSource();
                climateSampler = randomState.sampler();
                heightAccessor = LevelHeightAccessor.create(level.getMinY(), level.getHeight());
                seaLevel = level.getSeaLevel();
                waterSurfaceEnabled = level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD);
                generationContextReady = true;
            }
            if (!PENDING.add(workKey)) continue;

            TileGenerationJob job = new TileGenerationJob(workKey, generator, randomState,
                    heightAccessor, biomeSource, climateSampler, seaLevel, waterSurfaceEnabled);
            try {
                WORKER.execute(job);
                scheduled++;
            } catch (RejectedExecutionException rejected) {
                PENDING.remove(workKey);
                break;
            }
        }
    }

    /**
     * A plan rebuild can invalidate dozens of queued meshes. Remove those jobs
     * immediately instead of making the single worker wake up and reject them
     * one by one ahead of the newly relevant tiles.
     */
    private static int pruneObsoleteQueuedJobs() {
        int removed = 0;
        Runnable[] queuedTasks = WORKER.getQueue().toArray(new Runnable[0]);
        for (Runnable queuedTask : queuedTasks) {
            if (queuedTask instanceof TileGenerationJob job
                    && job.isObsolete()
                    && WORKER.getQueue().remove(queuedTask)) {
                PENDING.remove(job.workKey);
                removed++;
            }
        }
        return removed;
    }

    private static TileMesh generateTile(TileGenerationJob job, TileKey key,
                                         ChunkGenerator generator, RandomState randomState,
                                         LevelHeightAccessor heightAccessor, BiomeSource biomeSource,
                                         Climate.Sampler climateSampler, int seaLevel,
                                         boolean waterSurfaceEnabled, long generationStartedNanos) {
        int side = key.resolution + 1;
        int[] heights = new int[side * side];
        int[] colors = new int[side * side];
        int[] waterColors = new int[side * side];
        int originX = key.chunkX * 16;
        int originZ = key.chunkZ * 16;
        int spanBlocks = key.sizeChunks * 16;
        int[] sampleWorldX = new int[side];
        int[] sampleWorldZ = new int[side];
        for (int coordinate = 0; coordinate <= key.resolution; coordinate++) {
            sampleWorldX[coordinate] = FarTerrainLodMath.sampleWorldCoordinate(
                    originX, spanBlocks, coordinate, key.resolution);
            sampleWorldZ[coordinate] = FarTerrainLodMath.sampleWorldCoordinate(
                    originZ, spanBlocks, coordinate, key.resolution);
        }

        for (int z = 0; z <= key.resolution; z++) {
            if (job.isObsolete()) return null;
            int worldZ = sampleWorldZ[z];
            for (int x = 0; x <= key.resolution; x++) {
                int worldX = sampleWorldX[x];
                int height = generator.getBaseHeight(worldX, worldZ,
                        Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
                height = Math.max(heightAccessor.getMinY(),
                        Math.min(heightAccessor.getMaxY(), height));
                int index = z * side + x;
                heights[index] = height;
                Holder<Biome> biome = biomeSource.getNoiseBiome(
                        QuartPos.fromBlock(worldX), QuartPos.fromBlock(height),
                        QuartPos.fromBlock(worldZ), climateSampler);
                int waterColor = opaqueRgb(biome.value().getWaterColor());
                waterColors[index] = blendRgb(0xFF28566D, waterColor, 0.58f);
                colors[index] = terrainColor(height, seaLevel,
                        biome.value().getGrassColor(worldX, worldZ), biome.value().getWaterColor());
            }
        }

        double sampleSpacing = (double) spanBlocks / key.resolution;
        for (int z = 0; z <= key.resolution; z++) {
            if (job.isObsolete()) return null;
            for (int x = 0; x <= key.resolution; x++) {
                int index = z * side + x;
                int left = z * side + Math.max(0, x - 1);
                int right = z * side + Math.min(key.resolution, x + 1);
                int north = Math.max(0, z - 1) * side + x;
                int south = Math.min(key.resolution, z + 1) * side + x;
                double dx = (heights[right] - heights[left])
                        / (double) Math.max(1, (Math.min(key.resolution, x + 1)
                        - Math.max(0, x - 1)) * sampleSpacing);
                double dz = (heights[south] - heights[north])
                        / (double) Math.max(1, (Math.min(key.resolution, z + 1)
                        - Math.max(0, z - 1)) * sampleSpacing);

                // A fixed distant sun direction gives the coarse terrain enough shape
                // to read clearly without depending on block-light or client chunk data.
                double normalLength = Math.sqrt(dx * dx + dz * dz + 1.0);
                double diffuse = Math.max(0.0,
                        (-dx * -0.42 + 1.0 * 0.82 + -dz * -0.38) / normalLength);
                double brightness = 0.48 + diffuse * 0.52;
                colors[index] = shadeColor(colors[index], brightness);
            }
        }

        long[] waterCellMask = FarTerrainLodMath.createWaterCellMask(
                heights, key.resolution, seaLevel, waterSurfaceEnabled);
        return new TileMesh(job.workKey.revision(),
                (System.nanoTime() - generationStartedNanos) / 1_000_000.0,
                key, originX, originZ, spanBlocks, seaLevel,
                sampleWorldX, sampleWorldZ, heights, colors, waterColors, waterCellMask);
    }

    private static int shadeColor(int color, double brightness) {
        int red = Math.min(255, (int) Math.round(((color >>> 16) & 0xFF) * brightness));
        int green = Math.min(255, (int) Math.round(((color >>> 8) & 0xFF) * brightness));
        int blue = Math.min(255, (int) Math.round((color & 0xFF) * brightness));
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int terrainColor(int height, int seaLevel, int grassColor, int waterColor) {
        if (height <= seaLevel - 18) return blendRgb(0xFF244B69, opaqueRgb(waterColor), 0.38f);
        if (height <= seaLevel - 5) return blendRgb(0xFF3A7F9C, opaqueRgb(waterColor), 0.52f);
        if (height <= seaLevel + 2) return 0xFFC2B17B;
        if (height >= seaLevel + 112) return 0xFFE1E4E2;
        if (height >= seaLevel + 76) return 0xFF888985;
        return opaqueRgb(grassColor);
    }

    private static int opaqueRgb(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static int blendRgb(int first, int second, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int red = Math.round(((first >>> 16) & 0xFF) * (1.0f - t)
                + ((second >>> 16) & 0xFF) * t);
        int green = Math.round(((first >>> 8) & 0xFF) * (1.0f - t)
                + ((second >>> 8) & 0xFF) * t);
        int blue = Math.round((first & 0xFF) * (1.0f - t) + (second & 0xFF) * t);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static void drainCompletedMeshes() {
        for (int i = 0; i < 24; i++) {
            TileMesh mesh = COMPLETED.poll();
            if (mesh == null) break;
            if (!FarTerrainLodMath.isWorkObsolete(
                    mesh.revision, revision, DESIRED.contains(mesh.key))) {
                recordTileGeneration(mesh.generationMillis);
                TileMesh previous = MESHES.put(mesh.key, mesh);
                if (previous != null) cachedMeshBytes -= previous.estimatedBytes();
                cachedMeshBytes += mesh.estimatedBytes();
            }
        }
        trimMeshCache();
    }

    private static void recalculateMeshCacheBytes() {
        cachedMeshBytes = 0L;
        for (TileMesh mesh : MESHES.values()) cachedMeshBytes += mesh.estimatedBytes();
    }

    private static void trimMeshCache() {
        if (cachedMeshBytes <= MAX_MESH_CACHE_BYTES) return;
        // orderedTiles is nearest-first; discard the least useful distant meshes first.
        for (int i = orderedTiles.size() - 1; i >= 0 && cachedMeshBytes > MAX_MESH_CACHE_BYTES; i--) {
            TileKey key = orderedTiles.get(i);
            TileMesh removed = MESHES.remove(key);
            if (removed != null) {
                cachedMeshBytes -= removed.estimatedBytes();
                CACHE_EVICTED.add(key);
            }
        }
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!FarViewFeature.INSTANCE.isEnabled() || client.player == null || client.level == null
                || !client.hasSingleplayerServer() || activeLevel == null || orderedTiles.isEmpty()) return;

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.position();
        var cameraForward = camera.forwardVector();
        double forwardX = cameraForward.x();
        double forwardZ = cameraForward.z();
        double forwardLength = Math.hypot(forwardX, forwardZ);
        boolean hasHorizontalForward = forwardLength > 1.0E-5;
        if (hasHorizontalForward) {
            forwardX /= forwardLength;
            forwardZ /= forwardLength;
        }
        double cameraX = cameraPosition.x;
        double cameraY = cameraPosition.y;
        double cameraZ = cameraPosition.z;
        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        double innerRadius = (Math.max(2, FarViewController.getEffectiveRenderDistance()) + 6) * 16.0;
        double outerRadius = effectiveFarDistance * 16.0;
        double innerRadiusSquared = innerRadius * innerRadius;
        double outerRadiusSquared = outerRadius * outerRadius;

        long renderStartNanos = System.nanoTime();
        int drawBudget = getDrawBudget();
        Matrix4f positionMatrix = context.matrices().last().pose();
        if (context.consumers() == null) return;
        VertexConsumer consumer = context.consumers().getBuffer(FarTerrainRenderType.TYPE);
        // Reuse one position scratch vector for every emitted vertex. The PoseStack
        // VertexConsumer overload allocates a temporary Vector3f per vertex.
        Vector3f transformedVertex = new Vector3f();
        int drawnQuads = 0;
        int submittedMeshes = 0;
        for (TileKey key : orderedTiles) {
            TileMesh mesh = MESHES.get(key);
            if (mesh == null) continue;
            if (drawnQuads >= drawBudget) break;

            double tileCenterX = mesh.originX + mesh.spanBlocks * 0.5;
            double tileCenterZ = mesh.originZ + mesh.spanBlocks * 0.5;
            double tileDx = tileCenterX - playerX;
            double tileDz = tileCenterZ - playerZ;
            double tileDistanceSquared = tileDx * tileDx + tileDz * tileDz;
            double tileHalfSpan = mesh.spanBlocks * 0.5;
            double nearestDx = Math.max(0.0, Math.abs(tileDx) - tileHalfSpan);
            double nearestDz = Math.max(0.0, Math.abs(tileDz) - tileHalfSpan);
            double nearestTileDistanceSquared = nearestDx * nearestDx + nearestDz * nearestDz;
            if (nearestTileDistanceSquared > outerRadiusSquared) continue;

            // If every point in this tile lies inside the vanilla-detail radius,
            // skip it completely. If the tile lies wholly inside the drawable
            // annulus, its individual cells do not need distance checks.
            double farthestDx = Math.abs(tileDx) + tileHalfSpan;
            double farthestDz = Math.abs(tileDz) + tileHalfSpan;
            double farthestTileDistanceSquared = farthestDx * farthestDx + farthestDz * farthestDz;
            if (FarTerrainLodMath.isEntireTileInsideInnerRadius(
                    farthestTileDistanceSquared, innerRadiusSquared)) continue;
            boolean tileEntirelyInsideRenderBand = FarTerrainLodMath.isEntireTileInsideRenderBand(
                    nearestTileDistanceSquared, farthestTileDistanceSquared,
                    innerRadiusSquared, outerRadiusSquared);
            if (hasHorizontalForward && FarTerrainLodMath.isOutsideHorizontalViewCone(
                    tileDx, tileDz, forwardX, forwardZ, mesh.spanBlocks * 0.72)) {
                continue;
            }

            int resolution = mesh.key.resolution;
            int side = resolution + 1;
            int[] heights = mesh.heights;
            int[] colors = mesh.colors;
            int[] waterColors = mesh.waterColors;
            int[] sampleWorldX = mesh.sampleWorldX;
            int[] sampleWorldZ = mesh.sampleWorldZ;

            for (int z = 0; z < resolution && drawnQuads < drawBudget; z++) {
                double z0 = sampleWorldZ[z];
                double z1 = sampleWorldZ[z + 1];
                for (int x = 0; x < resolution && drawnQuads < drawBudget; x++) {
                    double x0 = sampleWorldX[x];
                    double x1 = sampleWorldX[x + 1];
                    if (!tileEntirelyInsideRenderBand) {
                        double centerX = (x0 + x1) * 0.5;
                        double centerZ = (z0 + z1) * 0.5;
                        double dx = centerX - playerX;
                        double dz = centerZ - playerZ;
                        double distanceSquared = dx * dx + dz * dz;
                        if (distanceSquared < innerRadiusSquared
                                || distanceSquared > outerRadiusSquared) continue;
                    }

                    int i00 = z * side + x;
                    int i10 = i00 + 1;
                    int i01 = i00 + side;
                    int i11 = i01 + 1;
                    boolean submerged = FarTerrainLodMath.isWaterCell(
                            mesh.waterCellMask, z * resolution + x);
                    if (submerged) {
                        // Distant water is a single opaque, biome-tinted sea-level surface.
                        // Full transparency is intentionally left to the nearby vanilla renderer.
                        double waterY = mesh.seaLevel + 0.04;
                        emitQuad(consumer, positionMatrix, transformedVertex, cameraX, cameraY, cameraZ,
                                x0, waterY, z0, waterColors[i00],
                                x0, waterY, z1, waterColors[i01],
                                x1, waterY, z1, waterColors[i11],
                                x1, waterY, z0, waterColors[i10]);
                    } else {
                        emitQuad(consumer, positionMatrix, transformedVertex, cameraX, cameraY, cameraZ,
                                x0, heights[i00], z0, colors[i00],
                                x0, heights[i01], z1, colors[i01],
                                x1, heights[i11], z1, colors[i11],
                                x1, heights[i10], z0, colors[i10]);
                    }
                    drawnQuads++;
                }
            }

            if (drawnQuads < drawBudget) {
                drawnQuads += emitSkirts(consumer, positionMatrix, transformedVertex, mesh, cameraX, cameraY, cameraZ,
                        playerX, playerZ, innerRadiusSquared, outerRadiusSquared,
                        drawnQuads, drawBudget);
            }
            submittedMeshes++;
        }
        lastDrawnQuads = drawnQuads;
        lastSubmittedMeshes = submittedMeshes;
        updateDrawBudget((System.nanoTime() - renderStartNanos) / 1_000_000.0);
    }

    private static int getDrawBudget() {
        int profileBudget = switch (FarViewFeature.getQuality()) {
            case "PERFORMANCE" -> 18_000;
            case "HIGH" -> 64_000;
            case "ULTRA" -> MAX_DRAW_QUADS;
            default -> 36_000;
        };
        return FarViewFeature.isAdaptiveEnabled()
                ? Math.max(12_000, Math.min(profileBudget, adaptiveDrawBudget))
                : profileBudget;
    }

    private static void recordTileGeneration(double generationMillis) {
        lastTileGenerationMs = generationMillis;
        averageTileGenerationMs = tileGenerationSamples == 0L
                ? generationMillis
                : averageTileGenerationMs + (generationMillis - averageTileGenerationMs) * 0.12;
        tileGenerationSamples++;
    }

    private static void updateDrawBudget(double renderMillis) {
        if (!FarViewFeature.isAdaptiveEnabled()) {
            drawBudgetAdjustmentFrames = 0;
            return;
        }
        averageMeshSubmitMs = averageMeshSubmitMs == 0.0
                ? renderMillis : averageMeshSubmitMs * 0.82 + renderMillis * 0.18;
        if (++drawBudgetAdjustmentFrames < 10) return;
        drawBudgetAdjustmentFrames = 0;
        adaptiveDrawBudget = FarTerrainLodMath.adjustDrawBudget(
                adaptiveDrawBudget, averageMeshSubmitMs, 12_000, MAX_DRAW_QUADS);
    }

    private static int emitSkirts(VertexConsumer consumer, Matrix4f positionMatrix,
                                  Vector3f transformedVertex, TileMesh mesh,
                                  double cameraX, double cameraY, double cameraZ,
                                  double playerX, double playerZ, double innerRadiusSquared,
                                  double outerRadiusSquared, int alreadyDrawn, int maxQuads) {
        int emitted = 0;
        int resolution = mesh.key.resolution;
        int side = resolution + 1;
        int[] heights = mesh.heights;
        int[] colors = mesh.colors;
        int[] sampleWorldX = mesh.sampleWorldX;
        int[] sampleWorldZ = mesh.sampleWorldZ;
        double step = (double) mesh.spanBlocks / resolution;
        int depth = Math.max(SKIRT_MIN_DEPTH, (int) Math.min(96, step * 0.2));

        for (int edge = 0; edge < 4 && alreadyDrawn + emitted < maxQuads; edge++) {
            for (int i = 0; i < resolution && alreadyDrawn + emitted < maxQuads; i++) {
                int a;
                int b;
                double ax;
                double az;
                double bx;
                double bz;
                if (edge == 0) {
                    a = i; b = i + 1;
                    ax = sampleWorldX[i]; az = sampleWorldZ[0];
                    bx = sampleWorldX[i + 1]; bz = sampleWorldZ[0];
                } else if (edge == 1) {
                    a = resolution * side + i; b = a + 1;
                    ax = sampleWorldX[i]; az = sampleWorldZ[resolution];
                    bx = sampleWorldX[i + 1]; bz = az;
                } else if (edge == 2) {
                    a = i * side; b = (i + 1) * side;
                    ax = sampleWorldX[0]; az = sampleWorldZ[i];
                    bx = ax; bz = sampleWorldZ[i + 1];
                } else {
                    a = i * side + resolution; b = (i + 1) * side + resolution;
                    ax = sampleWorldX[resolution]; az = sampleWorldZ[i];
                    bx = ax; bz = sampleWorldZ[i + 1];
                }

                double midX = (ax + bx) * 0.5;
                double midZ = (az + bz) * 0.5;
                double dx = midX - playerX;
                double dz = midZ - playerZ;
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared < innerRadiusSquared || distanceSquared > outerRadiusSquared) continue;

                int darkA = FarTerrainLodMath.darkenOpaqueArgb(colors[a]);
                int darkB = FarTerrainLodMath.darkenOpaqueArgb(colors[b]);
                emitQuad(consumer, positionMatrix, transformedVertex, cameraX, cameraY, cameraZ,
                        ax, heights[a], az, colors[a],
                        ax, heights[a] - depth, az, darkA,
                        bx, heights[b] - depth, bz, darkB,
                        bx, heights[b], bz, colors[b]);
                emitted++;
            }
        }
        return emitted;
    }

    private static void emitQuad(VertexConsumer consumer, Matrix4f positionMatrix,
                                 Vector3f transformedVertex,
                                 double cameraX, double cameraY, double cameraZ,
                                 double x0, double y0, double z0, int color0,
                                 double x1, double y1, double z1, int color1,
                                 double x2, double y2, double z2, int color2,
                                 double x3, double y3, double z3, int color3) {
        putVertex(consumer, positionMatrix, transformedVertex,
                x0 - cameraX, y0 - cameraY, z0 - cameraZ, color0);
        putVertex(consumer, positionMatrix, transformedVertex,
                x1 - cameraX, y1 - cameraY, z1 - cameraZ, color1);
        putVertex(consumer, positionMatrix, transformedVertex,
                x2 - cameraX, y2 - cameraY, z2 - cameraZ, color2);
        putVertex(consumer, positionMatrix, transformedVertex,
                x3 - cameraX, y3 - cameraY, z3 - cameraZ, color3);
    }

    private static void putVertex(VertexConsumer consumer, Matrix4f positionMatrix,
                                  Vector3f transformedVertex, double x, double y, double z,
                                  int color) {
        transformedVertex.set((float) x, (float) y, (float) z);
        positionMatrix.transformPosition(transformedVertex);
        consumer.addVertex(transformedVertex.x, transformedVertex.y, transformedVertex.z)
                .setColor(color);
    }

    private static double distanceSquaredToCenter(TileKey key, double playerChunkX, double playerChunkZ) {
        return distanceSquaredToCenter(key.chunkX, key.chunkZ, key.sizeChunks,
                playerChunkX, playerChunkZ);
    }

    private static double distanceSquaredToCenter(int minX, int minZ, int size,
                                                   double playerX, double playerZ) {
        double dx = minX + size * 0.5 - playerX;
        double dz = minZ + size * 0.5 - playerZ;
        return dx * dx + dz * dz;
    }

    private static void clearWorld() {
        if (activeLevel == null && MESHES.isEmpty() && DESIRED.isEmpty()) return;
        activeLevel = null;
        revision++;
        lastWorkerError = "";
        averageMeshSubmitMs = 0.0;
        adaptiveDrawBudget = 36_000;
        drawBudgetAdjustmentFrames = 0;
        DESIRED = Set.of();
        MESHES.clear();
        cachedMeshBytes = 0L;
        FAILED.clear();
        CACHE_EVICTED.clear();
        orderedTiles = List.of();
        COMPLETED.clear();
        WORKER.getQueue().clear();
        PENDING.removeIf(work -> work.revision != revision);
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
        lastSampleSpacing = -1;
        lastDetailDistance = -1;
        lastConfiguredFarDistance = -1;
        effectiveFarDistance = 512;
        ticksUntilAdaptiveUpdate = 0;
        lastTilePlanMs = 0.0;
        averageTileGenerationMs = 0.0;
        lastTileGenerationMs = 0.0;
        tilePlanBuildCount = 0L;
        tileGenerationSamples = 0L;
        lastDrawnQuads = 0;
        lastSubmittedMeshes = 0;
        workerBackoffNanos = 0L;
        lastPrunedQueuedJobs = 0;
        totalPrunedQueuedJobs = 0L;
    }

    public static String getStatusLine() {
        if (!FarViewFeature.INSTANCE.isEnabled()) return "Far terrain renderer is disabled";
        if (activeLevel == null) return "Far terrain LOD is available in singleplayer worlds";
        String suffix = lastWorkerError.isBlank() ? "" : " | worker warning: " + lastWorkerError;
        return "LOD tiles: " + MESHES.size() + "/" + DESIRED.size()
                + " ready | " + WORKER.getQueue().size() + " queued | " + FAILED.size()
                + " failed | " + CACHE_EVICTED.size() + " memory-evicted | cache "
                + (cachedMeshBytes / (1024 * 1024)) + "/"
                + (MAX_MESH_CACHE_BYTES / (1024 * 1024)) + " MiB | range "
                + effectiveFarDistance + " chunks | plan "
                + String.format(java.util.Locale.ROOT, "%.2f", lastTilePlanMs) + " ms/"
                + tilePlanBuildCount + " rebuilds | mesh avg "
                + String.format(java.util.Locale.ROOT, "%.2f", lastTileGenerationMs)
                + "/" + String.format(java.util.Locale.ROOT, "%.2f", averageTileGenerationMs)
                + " ms last/avg over " + tileGenerationSamples + " jobs | submit avg "
                + String.format(java.util.Locale.ROOT, "%.2f", averageMeshSubmitMs)
                + " ms | worker pause " + TimeUnit.NANOSECONDS.toMillis(workerBackoffNanos)
                + " ms | stale queue jobs " + lastPrunedQueuedJobs + " last / "
                + totalPrunedQueuedJobs + " total | draw " + lastDrawnQuads + "/" + getDrawBudget()
                + " quads, " + lastSubmittedMeshes + " tiles" + suffix;
    }

    private static final class TileGenerationJob implements Runnable {
        private final WorkKey workKey;
        private final ChunkGenerator generator;
        private final RandomState randomState;
        private final LevelHeightAccessor heightAccessor;
        private final BiomeSource biomeSource;
        private final Climate.Sampler climateSampler;
        private final int seaLevel;
        private final boolean waterSurfaceEnabled;

        private TileGenerationJob(WorkKey workKey, ChunkGenerator generator, RandomState randomState,
                                  LevelHeightAccessor heightAccessor, BiomeSource biomeSource,
                                  Climate.Sampler climateSampler, int seaLevel,
                                  boolean waterSurfaceEnabled) {
            this.workKey = workKey;
            this.generator = generator;
            this.randomState = randomState;
            this.heightAccessor = heightAccessor;
            this.biomeSource = biomeSource;
            this.climateSampler = climateSampler;
            this.seaLevel = seaLevel;
            this.waterSurfaceEnabled = waterSurfaceEnabled;
        }

        private boolean isObsolete() {
            return FarTerrainLodMath.isWorkObsolete(
                    workKey.revision(), revision, DESIRED.contains(workKey.key()));
        }

        @Override
        public void run() {
            boolean generated = false;
            try {
                if (isObsolete()) return;
                long generationStartedNanos = System.nanoTime();
                TileMesh mesh = generateTile(this, workKey.key(), generator, randomState,
                        heightAccessor, biomeSource, climateSampler, seaLevel, waterSurfaceEnabled,
                        generationStartedNanos);
                if (mesh != null && !isObsolete()) {
                    COMPLETED.add(mesh);
                    generated = true;
                }
            } catch (RuntimeException | LinkageError error) {
                // A job from an old world revision must never poison the new world's tile state.
                if (!isObsolete()) {
                    FAILED.add(workKey.key());
                    String message = error.getMessage();
                    lastWorkerError = error.getClass().getSimpleName()
                            + (message == null || message.isBlank() ? "" : ": " + message);
                    if (lastWorkerError.length() > 180) {
                        lastWorkerError = lastWorkerError.substring(0, 177) + "...";
                    }
                }
            } finally {
                PENDING.remove(workKey);
                if (generated && !isObsolete()) {
                    long backoff = workerBackoffNanos;
                    if (backoff > 0L) LockSupport.parkNanos(backoff);
                }
            }
        }
    }

    private record TileKey(int chunkX, int chunkZ, int sizeChunks, int resolution) {}
    private record WorkKey(TileKey key, long revision) {}
    private record TileMesh(long revision, double generationMillis,
                            TileKey key, int originX, int originZ, int spanBlocks,
                            int seaLevel, int[] sampleWorldX, int[] sampleWorldZ,
                            int[] heights, int[] colors, int[] waterColors, long[] waterCellMask) {
        private long estimatedBytes() {
            return ((long) sampleWorldX.length + sampleWorldZ.length
                    + heights.length + colors.length + waterColors.length) * Integer.BYTES
                    + (long) waterCellMask.length * Long.BYTES;
        }
    }
}
