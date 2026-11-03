package name.vantaclient.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import name.vantaclient.client.feature.FarViewFeature;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.Vec3;

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
    private static final int DESIRED_REFRESH_TICKS = 20;
    private static final int ADAPTIVE_REFRESH_TICKS = 40;
    private static final int MAX_TILES_TO_SCHEDULE_PER_TICK = 6;
    private static final int MAX_QUEUED_TILE_JOBS = 96;
    private static final int MAX_DRAW_QUADS = 320_000;
    private static final int MAX_DESIRED_TILES = 4096;
    private static final int SKIRT_MIN_DEPTH = 24;

    private static final Set<WorkKey> PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<TileKey> FAILED = ConcurrentHashMap.newKeySet();
    private static final Set<TileKey> DESIRED = ConcurrentHashMap.newKeySet();
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
    private static List<TileKey> orderedTiles = List.of();

    private static ServerLevel activeLevel;
    private static volatile long revision;
    private static int ticksUntilRefresh;
    private static int ticksUntilAdaptiveUpdate;
    private static int effectiveFarDistance = 512;
    private static int lastResolution = -1;
    private static int lastDetailDistance = -1;
    private static int lastConfiguredFarDistance = -1;
    private static int lastPlayerChunkX = Integer.MIN_VALUE;
    private static int lastPlayerChunkZ = Integer.MIN_VALUE;
    private static volatile String lastWorkerError = "";

    private FarTerrainRenderer() {}

    public static void register() {
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
        drainCompletedMeshes();

        int playerChunkX = client.player.chunkPosition().x;
        int playerChunkZ = client.player.chunkPosition().z;
        int resolution = FarViewFeature.getSampleResolution();
        int detailDistance = Math.max(2, FarViewController.getEffectiveRenderDistance());
        int configuredFarDistance = effectiveFarDistance;

        boolean movedEnough = Math.abs(playerChunkX - lastPlayerChunkX) >= 4
                || Math.abs(playerChunkZ - lastPlayerChunkZ) >= 4;
        boolean settingsChanged = resolution != lastResolution
                || detailDistance != lastDetailDistance
                || configuredFarDistance != lastConfiguredFarDistance;

        if (ticksUntilRefresh-- <= 0 || movedEnough || settingsChanged) {
            ticksUntilRefresh = DESIRED_REFRESH_TICKS;
            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkZ = playerChunkZ;
            lastResolution = resolution;
            lastDetailDistance = detailDistance;
            lastConfiguredFarDistance = configuredFarDistance;
            rebuildDesiredTiles(playerChunkX, playerChunkZ, detailDistance,
                    configuredFarDistance, resolution);
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
            effectiveFarDistance = Math.max(64, effectiveFarDistance - 64);
        } else if (hasHeadroom && effectiveFarDistance < target) {
            effectiveFarDistance = Math.min(target, effectiveFarDistance + 32);
        }
    }

    private static void rebuildDesiredTiles(int playerChunkX, int playerChunkZ,
                                             int detailDistance, int farDistance, int resolution) {
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
                        resolution, nextDesired);
            }
        }

        List<TileKey> sorted = new ArrayList<>(nextDesired);
        sorted.sort(Comparator.comparingDouble(key -> distanceSquaredToCenter(
                key, playerChunkX + 0.5, playerChunkZ + 0.5)));

        if (nextDesired.size() > MAX_DESIRED_TILES) {
            nextDesired.removeIf(key -> distanceSquaredToCenter(key,
                    playerChunkX + 0.5, playerChunkZ + 0.5)
                    > (double) farDistance * farDistance * 0.92);
        }
        DESIRED.clear();
        DESIRED.addAll(nextDesired);
        MESHES.keySet().removeIf(key -> !nextDesired.contains(key));
        orderedTiles = List.copyOf(sorted);
    }

    private static void collectLeaves(int minChunkX, int minChunkZ, int size,
                                      int playerChunkX, int playerChunkZ,
                                      int detailDistance, int farDistance, int resolution,
                                      Set<TileKey> output) {
        double nearestDistance = distanceToRectangle(playerChunkX + 0.5, playerChunkZ + 0.5,
                minChunkX, minChunkZ, minChunkX + size, minChunkZ + size);
        if (nearestDistance > farDistance) return;

        double farthestDistance = farthestCornerDistance(playerChunkX + 0.5, playerChunkZ + 0.5,
                minChunkX, minChunkZ, minChunkX + size, minChunkZ + size);
        if (farthestDistance < Math.max(1, detailDistance - 1)) return;

        double centerDistance = Math.sqrt(distanceSquaredToCenter(
                minChunkX, minChunkZ, size, playerChunkX + 0.5, playerChunkZ + 0.5));
        int desiredSize = desiredTileSize(centerDistance);

        if (size > desiredSize && size > MIN_TILE_SIZE_CHUNKS) {
            int half = size / 2;
            collectLeaves(minChunkX, minChunkZ, half, playerChunkX, playerChunkZ,
                    detailDistance, farDistance, resolution, output);
            collectLeaves(minChunkX + half, minChunkZ, half, playerChunkX, playerChunkZ,
                    detailDistance, farDistance, resolution, output);
            collectLeaves(minChunkX, minChunkZ + half, half, playerChunkX, playerChunkZ,
                    detailDistance, farDistance, resolution, output);
            collectLeaves(minChunkX + half, minChunkZ + half, half, playerChunkX, playerChunkZ,
                    detailDistance, farDistance, resolution, output);
            return;
        }

        int tileResolution = resolutionForDistance(centerDistance, resolution);
        output.add(new TileKey(minChunkX, minChunkZ, size, tileResolution));
    }

    private static int resolutionForDistance(double distanceChunks, int profileResolution) {
        if (distanceChunks >= 1536) return Math.max(4, profileResolution / 2);
        if (distanceChunks >= 768) return Math.max(4, profileResolution * 3 / 4);
        return profileResolution;
    }

    private static int desiredTileSize(double distanceChunks) {
        if (distanceChunks < 64) return 8;
        if (distanceChunks < 128) return 16;
        if (distanceChunks < 256) return 32;
        if (distanceChunks < 512) return 64;
        if (distanceChunks < 1024) return 128;
        if (distanceChunks < 1536) return 256;
        return 512;
    }

    private static void scheduleMissingTiles(ServerLevel level) {
        int scheduled = 0;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();

        for (TileKey key : orderedTiles) {
            if (scheduled >= MAX_TILES_TO_SCHEDULE_PER_TICK) break;
            if (MESHES.containsKey(key) || FAILED.contains(key)) continue;
            WorkKey workKey = new WorkKey(key, revision);
            if (!PENDING.add(workKey)) continue;

            long taskRevision = revision;
            int seaLevel = level.getSeaLevel();
            LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(level.getMinY(), level.getHeight());
            try {
                WORKER.execute(() -> {
                    try {
                        if (taskRevision != revision || !DESIRED.contains(key)) return;
                        TileMesh mesh = generateTile(key, generator, randomState, heightAccessor, seaLevel);
                        if (taskRevision == revision && DESIRED.contains(key)) COMPLETED.add(mesh);
                    } catch (RuntimeException error) {
                        FAILED.add(key);
                        lastWorkerError = error.getClass().getSimpleName() + ": " + error.getMessage();
                    } finally {
                        PENDING.remove(workKey);
                    }
                });
                scheduled++;
            } catch (RejectedExecutionException rejected) {
                PENDING.remove(workKey);
                break;
            }
        }
    }

    private static TileMesh generateTile(TileKey key, ChunkGenerator generator,
                                         RandomState randomState, LevelHeightAccessor heightAccessor,
                                         int seaLevel) {
        int side = key.resolution + 1;
        int[] heights = new int[side * side];
        int[] colors = new int[side * side];
        int originX = key.chunkX * 16;
        int originZ = key.chunkZ * 16;
        int spanBlocks = key.sizeChunks * 16;

        for (int z = 0; z <= key.resolution; z++) {
            int worldZ = originZ + Math.round((float) z * spanBlocks / key.resolution);
            for (int x = 0; x <= key.resolution; x++) {
                int worldX = originX + Math.round((float) x * spanBlocks / key.resolution);
                int height = generator.getBaseHeight(worldX, worldZ,
                        Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
                height = Math.max(heightAccessor.getMinY(),
                        Math.min(heightAccessor.getMaxY(), height));
                int index = z * side + x;
                heights[index] = height;
            }
        }

        double sampleSpacing = (double) spanBlocks / key.resolution;
        for (int z = 0; z <= key.resolution; z++) {
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
                colors[index] = shadeColor(terrainColor(heights[index], seaLevel), brightness);
            }
        }

        return new TileMesh(key, originX, originZ, spanBlocks, heights, colors);
    }

    private static int shadeColor(int color, double brightness) {
        int red = Math.min(255, (int) Math.round(((color >>> 16) & 0xFF) * brightness));
        int green = Math.min(255, (int) Math.round(((color >>> 8) & 0xFF) * brightness));
        int blue = Math.min(255, (int) Math.round((color & 0xFF) * brightness));
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int terrainColor(int height, int seaLevel) {
        if (height <= seaLevel - 18) return 0xFF254F70;
        if (height <= seaLevel - 5) return 0xFF347A9C;
        if (height <= seaLevel + 2) return 0xFFC2B17B;
        if (height >= seaLevel + 112) return 0xFFE1E4E2;
        if (height >= seaLevel + 76) return 0xFF888985;

        float elevation = Math.max(0.0f, Math.min(1.0f, (height - seaLevel) / 100.0f));
        int red = Math.round(79 - elevation * 18);
        int green = Math.round(133 - elevation * 24);
        int blue = Math.round(65 - elevation * 12);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static void drainCompletedMeshes() {
        for (int i = 0; i < 24; i++) {
            TileMesh mesh = COMPLETED.poll();
            if (mesh == null) break;
            if (DESIRED.contains(mesh.key)) {
                MESHES.put(mesh.key, mesh);
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

        PoseStack.Pose pose = context.matrices().last();
        if (context.consumers() == null) return;
        VertexConsumer consumer = context.consumers().getBuffer(RenderTypes.debugQuads());
        int drawnQuads = 0;
        for (TileKey key : orderedTiles) {
            TileMesh mesh = MESHES.get(key);
            if (mesh == null) continue;
            if (drawnQuads >= MAX_DRAW_QUADS) break;

            double tileCenterX = mesh.originX + mesh.spanBlocks * 0.5;
            double tileCenterZ = mesh.originZ + mesh.spanBlocks * 0.5;
            double tileDx = tileCenterX - playerX;
            double tileDz = tileCenterZ - playerZ;
            double tileDistanceSquared = tileDx * tileDx + tileDz * tileDz;
            if (tileDistanceSquared > outerRadiusSquared * 1.08) continue;
            double tileDistance = Math.sqrt(tileDistanceSquared);
            if (hasHorizontalForward && tileDistance > 1.0) {
                double angularRadius = Math.atan2(mesh.spanBlocks * 0.72, tileDistance);
                double viewDot = (tileDx * forwardX + tileDz * forwardZ) / tileDistance;
                if (viewDot < Math.cos(Math.toRadians(68.0) + angularRadius)) continue;
            }

            int resolution = mesh.key.resolution;
            int side = resolution + 1;
            int[] heights = mesh.heights;
            int[] colors = mesh.colors;
            double step = (double) mesh.spanBlocks / resolution;

            for (int z = 0; z < resolution && drawnQuads < MAX_DRAW_QUADS; z++) {
                double z0 = mesh.originZ + z * step;
                double z1 = mesh.originZ + (z + 1) * step;
                for (int x = 0; x < resolution && drawnQuads < MAX_DRAW_QUADS; x++) {
                    double x0 = mesh.originX + x * step;
                    double x1 = mesh.originX + (x + 1) * step;
                    double centerX = (x0 + x1) * 0.5;
                    double centerZ = (z0 + z1) * 0.5;
                    double dx = centerX - playerX;
                    double dz = centerZ - playerZ;
                    double distanceSquared = dx * dx + dz * dz;
                    if (distanceSquared < innerRadiusSquared || distanceSquared > outerRadiusSquared) continue;

                    int i00 = z * side + x;
                    int i10 = i00 + 1;
                    int i01 = i00 + side;
                    int i11 = i01 + 1;
                    emitQuad(consumer, pose, cameraX, cameraY, cameraZ,
                            x0, heights[i00], z0, colors[i00],
                            x0, heights[i01], z1, colors[i01],
                            x1, heights[i11], z1, colors[i11],
                            x1, heights[i10], z0, colors[i10]);
                    drawnQuads++;
                }
            }

            if (drawnQuads < MAX_DRAW_QUADS) {
                drawnQuads += emitSkirts(consumer, pose, mesh, cameraX, cameraY, cameraZ,
                        playerX, playerZ, innerRadiusSquared, outerRadiusSquared,
                        drawnQuads, MAX_DRAW_QUADS);
            }
        }
    }

    private static int emitSkirts(VertexConsumer consumer, PoseStack.Pose pose, TileMesh mesh,
                                  double cameraX, double cameraY, double cameraZ,
                                  double playerX, double playerZ, double innerRadiusSquared,
                                  double outerRadiusSquared, int alreadyDrawn, int maxQuads) {
        int emitted = 0;
        int resolution = mesh.key.resolution;
        int side = resolution + 1;
        int[] heights = mesh.heights;
        int[] colors = mesh.colors;
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
                    ax = mesh.originX + i * step; az = mesh.originZ;
                    bx = mesh.originX + (i + 1) * step; bz = mesh.originZ;
                } else if (edge == 1) {
                    a = resolution * side + i; b = a + 1;
                    ax = mesh.originX + i * step; az = mesh.originZ + mesh.spanBlocks;
                    bx = mesh.originX + (i + 1) * step; bz = az;
                } else if (edge == 2) {
                    a = i * side; b = (i + 1) * side;
                    ax = mesh.originX; az = mesh.originZ + i * step;
                    bx = ax; bz = mesh.originZ + (i + 1) * step;
                } else {
                    a = i * side + resolution; b = (i + 1) * side + resolution;
                    ax = mesh.originX + mesh.spanBlocks; az = mesh.originZ + i * step;
                    bx = ax; bz = mesh.originZ + (i + 1) * step;
                }

                double midX = (ax + bx) * 0.5;
                double midZ = (az + bz) * 0.5;
                double dx = midX - playerX;
                double dz = midZ - playerZ;
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared < innerRadiusSquared || distanceSquared > outerRadiusSquared) continue;

                int darkA = darken(colors[a]);
                int darkB = darken(colors[b]);
                emitQuad(consumer, pose, cameraX, cameraY, cameraZ,
                        ax, heights[a], az, colors[a],
                        ax, heights[a] - depth, az, darkA,
                        bx, heights[b] - depth, bz, darkB,
                        bx, heights[b], bz, colors[b]);
                emitted++;
            }
        }
        return emitted;
    }

    private static int darken(int color) {
        int red = ((color >>> 16) & 0xFF) / 2;
        int green = ((color >>> 8) & 0xFF) / 2;
        int blue = (color & 0xFF) / 2;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static void emitQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                 double cameraX, double cameraY, double cameraZ,
                                 double x0, int y0, double z0, int color0,
                                 double x1, int y1, double z1, int color1,
                                 double x2, int y2, double z2, int color2,
                                 double x3, int y3, double z3, int color3) {
        putVertex(consumer, pose, x0 - cameraX, y0 - cameraY, z0 - cameraZ, color0);
        putVertex(consumer, pose, x1 - cameraX, y1 - cameraY, z1 - cameraZ, color1);
        putVertex(consumer, pose, x2 - cameraX, y2 - cameraY, z2 - cameraZ, color2);
        putVertex(consumer, pose, x3 - cameraX, y3 - cameraY, z3 - cameraZ, color3);
    }

    private static void putVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                  double x, double y, double z, int color) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor((color >>> 16) & 0xFF, (color >>> 8) & 0xFF,
                        color & 0xFF, 255);
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

    private static double distanceToRectangle(double x, double z,
                                              double minX, double minZ, double maxX, double maxZ) {
        double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0.0;
        double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0.0;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double farthestCornerDistance(double x, double z,
                                                  double minX, double minZ, double maxX, double maxZ) {
        double dx = Math.max(Math.abs(x - minX), Math.abs(x - maxX));
        double dz = Math.max(Math.abs(z - minZ), Math.abs(z - maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void clearWorld() {
        if (activeLevel == null && MESHES.isEmpty() && DESIRED.isEmpty()) return;
        activeLevel = null;
        revision++;
        lastWorkerError = "";
        DESIRED.clear();
        MESHES.clear();
        FAILED.clear();
        orderedTiles = List.of();
        COMPLETED.clear();
        WORKER.getQueue().clear();
        PENDING.removeIf(work -> work.revision != revision);
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
        lastResolution = -1;
        lastDetailDistance = -1;
        lastConfiguredFarDistance = -1;
        effectiveFarDistance = 512;
        ticksUntilRefresh = 0;
        ticksUntilAdaptiveUpdate = 0;
    }

    public static String getStatusLine() {
        if (!FarViewFeature.INSTANCE.isEnabled()) return "Far terrain renderer is disabled";
        if (activeLevel == null) return "Far terrain LOD is available in singleplayer worlds";
        String suffix = lastWorkerError.isBlank() ? "" : " | worker warning: " + lastWorkerError;
        return "LOD tiles: " + MESHES.size() + "/" + DESIRED.size()
                + " ready | " + WORKER.getQueue().size() + " queued | " + FAILED.size()
                + " failed | range " + effectiveFarDistance + " chunks" + suffix;
    }

    private record TileKey(int chunkX, int chunkZ, int sizeChunks, int resolution) {}
    private record WorkKey(TileKey key, long revision) {}
    private record TileMesh(TileKey key, int originX, int originZ, int spanBlocks,
                            int[] heights, int[] colors) {}
}
