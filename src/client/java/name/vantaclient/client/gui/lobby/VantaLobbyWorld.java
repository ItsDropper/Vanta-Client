package name.vantaclient.client.gui.lobby;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.stats.StatsCounter;

import java.util.List;
import java.lang.reflect.Method;

/**
 * Owns an isolated, generated ClientLevel for the title screen.
 *
 * The level is only swapped into Minecraft while GameRenderer is rendering.
 * The actual menu screen, input routing and title-screen lifecycle remain owned
 * by vanilla. No panorama texture is involved in this scene.
 */
public final class VantaLobbyWorld {
    private static final int VIEW_DISTANCE = 5;
    private static final int CENTER_X = 0;
    private static final int CENTER_Z = 0;

    private static ClientLevel level;
    private static ClientPacketListener listener;
    private static MultiPlayerGameMode gameMode;
    private static LocalPlayer cameraRig;
    private static boolean active;
    private static boolean swapped;
    private static ClientLevel previousLevel;
    private static LocalPlayer previousPlayer;
    private static MultiPlayerGameMode previousGameMode;
    private static Entity previousCamera;
    private static net.minecraft.client.CameraType previousCameraType;
    private static int previousServerRenderDistance;
    private static boolean sodiumLookupDone;
    private static Method sodiumTrackerLookup;
    private static Method sodiumChunkStatus;

    private VantaLobbyWorld() {}

    public static boolean isActive() {
        return active && level != null;
    }

    public static void start() {
        if (active || Minecraft.getInstance().level != null) return;
        try {
            build();
            active = true;
        } catch (Throwable error) {
            destroy();
            java.util.logging.Logger.getLogger("VantaClient").log(java.util.logging.Level.SEVERE, "Unable to create the Vanta title world", error);
        }
    }

    public static void stop() {
        restore();
        destroy();
    }

    /** Called at GameRenderer.render HEAD; restores the real client state at RETURN. */
    public static void beginFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level != level) {
            destroy();
            return;
        }
        if (!isActive() || !(mc.screen instanceof TitleScreen) || mc.level != null || swapped) return;
        previousLevel = mc.level;
        previousPlayer = mc.player;
        previousGameMode = mc.gameMode;
        previousCamera = mc.getCameraEntity();
        previousCameraType = mc.options.getCameraType();
        previousServerRenderDistance = mc.options.getEffectiveRenderDistance();
        try {
            VantaLobbyCamera camera = VantaLobbyScene.camera();
            float yaw = 180.0f + camera.subjectYaw();
            float pitch = camera.subjectPitch();
            cameraRig.setYRot(yaw);
            cameraRig.setYHeadRot(yaw);
            cameraRig.setYBodyRot(yaw);
            cameraRig.setXRot(pitch);
            mc.level = level;
            mc.player = cameraRig;
            mc.gameMode = gameMode;
            mc.setCameraEntity(cameraRig);
            mc.options.setServerRenderDistance(VIEW_DISTANCE);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            swapped = true;
        } catch (Throwable error) {
            restore();
            java.util.logging.Logger.getLogger("VantaClient").log(java.util.logging.Level.SEVERE, "Unable to activate the Vanta title world for rendering", error);
        }
    }

    public static void endFrame() {
        restore();
    }

    private static void restore() {
        if (!swapped) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.level = previousLevel;
            mc.player = previousPlayer;
            mc.gameMode = previousGameMode;
            mc.options.setServerRenderDistance(previousServerRenderDistance);
            mc.options.setCameraType(previousCameraType);
            mc.setCameraEntity(previousCamera);
        } finally {
            previousLevel = null;
            previousPlayer = null;
            previousGameMode = null;
            previousCamera = null;
            previousCameraType = null;
            previousServerRenderDistance = 0;
            swapped = false;
        }
    }

    private static void build() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        RegistryAccess.Frozen registries = loadRegistries();
        CommonListenerCookie cookie = new CommonListenerCookie(
                new LevelLoadTracker(),
                mc.getGameProfile(),
                mc.getTelemetryManager().createWorldSessionManager(false, null, null),
                registries,
                FeatureFlags.DEFAULT_FLAGS,
                "vanta-title-lobby",
                null,
                null,
                java.util.Map.of(),
                null,
                java.util.Map.of(),
                ServerLinks.EMPTY,
                java.util.Map.of(),
                false
        );
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        listener = new ClientPacketListener(mc, connection, cookie);
        Holder<?> dimension = registries.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        level = new ClientLevel(
                listener,
                new ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, false),
                Level.OVERWORLD,
                (Holder) dimension,
                VIEW_DISTANCE,
                2,
                mc.levelRenderer,
                false,
                0L,
                63
        );
        mc.levelRenderer.setLevel(level);
        mc.gameRenderer.setLevel(level);
        level.getChunkSource().updateViewCenter(CENTER_X, CENTER_Z);
        generateTerrain(level);
        level.setTimeFromServer(0L, 6000L, false);
        gameMode = new MultiPlayerGameMode(mc, listener);
        cameraRig = gameMode.createPlayer(level, new StatsCounter(), new ClientRecipeBook());
        cameraRig.noPhysics = true;
        cameraRig.setNoGravity(true);
        cameraRig.setPos(0.5, 64.0, -5.5);
        cameraRig.setYRot(180.0f);
        cameraRig.setXRot(4.0f);
        cameraRig.setYHeadRot(180.0f);
        cameraRig.setYBodyRot(180.0f);
        cameraRig.setId(0x56414E54);
        level.addEntity(cameraRig);
    }

    private static RegistryAccess.Frozen loadRegistries() throws Exception {
        try (MultiPackResourceManager resources = new MultiPackResourceManager(
                PackType.SERVER_DATA, List.<PackResources>of(ServerPacksSource.createVanillaPackSource()))) {
            var layers = RegistryLayer.createRegistryAccess();
            var staticTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
            staticTags.forEach(tags -> tags.apply());
            var worldLayer = RegistryLayer.WORLDGEN;
            var worldContext = layers.getAccessForLoading(worldLayer);
            List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(worldContext, staticTags);
            var world = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
            var dimensionContext = new java.util.ArrayList<>(lookups);
            dimensionContext.addAll(world.registries()
                    .map(entry -> (HolderLookup.RegistryLookup<?>) entry.value()).toList());
            var dimensions = RegistryDataLoader.load(resources, dimensionContext, RegistryDataLoader.DIMENSION_REGISTRIES);
            return layers.replaceFrom(worldLayer, world, dimensions).compositeAccess();
        }
    }

    private static void generateTerrain(ClientLevel world) {
        PalettedContainerFactory factory = world.palettedContainerFactory();
        int sectionCount = world.getSectionsCount();
        int minSection = world.getMinSectionY();
        for (int cx = -VIEW_DISTANCE; cx <= VIEW_DISTANCE; cx++) {
            for (int cz = -VIEW_DISTANCE; cz <= VIEW_DISTANCE; cz++) {
                LevelChunkSection[] sections = new LevelChunkSection[sectionCount];
                for (int i = 0; i < sectionCount; i++) sections[i] = new LevelChunkSection(factory);
                int chunkMinY = world.getMinY();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int wx = cx * 16 + x;
                        int wz = cz * 16 + z;
                        int ground = Math.abs(wx) <= 7 && wz >= -35 && wz <= -19
                                || Math.abs(wx) <= 2 && wz >= -20 && wz <= -4
                                ? 63 : terrainHeight(wx, wz);
                        for (int y = ground - 4; y <= ground; y++) {
                            BlockState state = y == ground ? Blocks.GRASS_BLOCK.defaultBlockState()
                                    : y >= ground - 2 ? Blocks.DIRT.defaultBlockState()
                                    : Blocks.STONE.defaultBlockState();
                            put(sections, minSection, chunkMinY, x, y, z, state);
                        }
                        if (ground < 61) {
                            for (int y = ground + 1; y <= 61; y++)
                                put(sections, minSection, chunkMinY, x, y, z, Blocks.WATER.defaultBlockState());
                        }
                        if (!(Math.abs(wx) <= 7 && wz >= -35 && wz <= -19)
                                && !(Math.abs(wx) <= 2 && wz >= -20 && wz <= -4)
                                && ground > 62 && Math.floorMod(wx * 31 + wz * 17, 137) == 0) {
                            growTree(sections, minSection, chunkMinY, x, ground + 1, z);
                        } else if (!(Math.abs(wx) <= 7 && wz >= -35 && wz <= -19)
                                && !(Math.abs(wx) <= 2 && wz >= -20 && wz <= -4)
                                && ground >= 62 && Math.floorMod(wx * 19 + wz * 13, 43) == 0) {
                            put(sections, minSection, chunkMinY, x, ground + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                        }
                    }
                }
                buildLobbyArchitecture(sections, minSection, chunkMinY, cx, cz);
                ChunkPos pos = new ChunkPos(cx, cz);
                LevelChunk chunk = new LevelChunk(world, pos, UpgradeData.EMPTY,
                        new LevelChunkTicks(), new LevelChunkTicks(), 0L, sections, null, null);
                Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
                ClientboundLevelChunkPacketData packet = new ClientboundLevelChunkPacketData(chunk);
                LevelChunk loaded = world.getChunkSource().replaceWithPacketData(cx, cz, packet.getReadBuffer(), packet.getHeightmaps(),
                        packet.getBlockEntitiesTagsConsumer(cx, cz));
                if (loaded != null) {
                    ChunkPos loadedPos = new ChunkPos(cx, cz);
                    Minecraft.getInstance().levelRenderer.onChunkReadyToRender(loaded.getPos());
                    world.getLightEngine().setLightEnabled(loadedPos, true);
                    for (int skySection = 5; skySection < world.getLightEngine().getMaxLightSection(); skySection++) {
                        world.getLightEngine().queueSectionData(LightLayer.SKY,
                                SectionPos.of(loadedPos, skySection), new DataLayer(15));
                    }
                    for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                        world.getLightEngine().updateSectionStatus(
                                SectionPos.of(loadedPos, minSection + sectionIndex), sections[sectionIndex].hasOnlyAir());
                    }

                }
            }
        }
        for (int pass = 0; world.getLightEngine().hasLightWork() && pass < 64; pass++) {
            world.getLightEngine().runLightUpdates();
        }

        // Match PolyPlus's post-population requeue: the LevelRenderer's view area
        // exists before chunks are inserted, so notify its occlusion graph only
        // after every chunk and its lighting data are ready.
        for (int cx = -VIEW_DISTANCE; cx <= VIEW_DISTANCE; cx++) {
            for (int cz = -VIEW_DISTANCE; cz <= VIEW_DISTANCE; cz++) {
                LevelChunk chunk = world.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                Minecraft.getInstance().levelRenderer.onChunkReadyToRender(chunk.getPos());
                notifySodiumChunkReady(world, cx, cz);
            }
        }
    }

    private static void buildLobbyArchitecture(LevelChunkSection[] sections, int minSection,
                                                int minY, int chunkX, int chunkZ) {
        // A broad stone forecourt gives the scene a deliberate architectural focal point.
        for (int x = -7; x <= 7; x++) {
            for (int z = -35; z <= -19; z++) {
                putWorld(sections, minSection, minY, chunkX, chunkZ, x, 63, z,
                        Math.abs(x) == 7 || z == -35 || z == -19
                                ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                                : Blocks.STONE_BRICKS.defaultBlockState());
            }
        }

        // A covered timber pavilion, with a layered roof and warm hanging lanterns.
        for (int y = 64; y <= 70; y++) {
            putWorld(sections, minSection, minY, chunkX, chunkZ, -6, y, -32, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            putWorld(sections, minSection, minY, chunkX, chunkZ, 6, y, -32, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            putWorld(sections, minSection, minY, chunkX, chunkZ, -6, y, -22, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            putWorld(sections, minSection, minY, chunkX, chunkZ, 6, y, -22, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
        }
        for (int x = -6; x <= 6; x++) {
            putWorld(sections, minSection, minY, chunkX, chunkZ, x, 70, -32, Blocks.DARK_OAK_LOG.defaultBlockState());
            putWorld(sections, minSection, minY, chunkX, chunkZ, x, 70, -22, Blocks.DARK_OAK_LOG.defaultBlockState());
        }
        for (int z = -32; z <= -22; z++) {
            putWorld(sections, minSection, minY, chunkX, chunkZ, -6, 70, z, Blocks.DARK_OAK_LOG.defaultBlockState());
            putWorld(sections, minSection, minY, chunkX, chunkZ, 6, 70, z, Blocks.DARK_OAK_LOG.defaultBlockState());
        }
        for (int x = -7; x <= 7; x++) {
            for (int z = -33; z <= -21; z++) {
                putWorld(sections, minSection, minY, chunkX, chunkZ, x, 71, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                if (Math.abs(x) == 7 || z == -33 || z == -21) {
                    putWorld(sections, minSection, minY, chunkX, chunkZ, x, 72, z, Blocks.DARK_OAK_SLAB.defaultBlockState());
                }
            }
        }
        for (int x : new int[]{-5, 5}) {
            for (int z : new int[]{-31, -23}) {
                putWorld(sections, minSection, minY, chunkX, chunkZ, x, 69, z, Blocks.LANTERN.defaultBlockState());
            }
        }

        // A centered approach connects the player position to the pavilion.
        for (int z = -20; z <= -4; z++) {
            for (int x = -1; x <= 1; x++) {
                putWorld(sections, minSection, minY, chunkX, chunkZ, x, 63, z,
                        x == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                                : Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
    }

    private static void putWorld(LevelChunkSection[] sections, int minSection, int minY,
                                 int chunkX, int chunkZ, int x, int y, int z, BlockState state) {
        if (Math.floorDiv(x, 16) != chunkX || Math.floorDiv(z, 16) != chunkZ) return;
        put(sections, minSection, minY, Math.floorMod(x, 16), y, Math.floorMod(z, 16), state);
    }

    private static void notifySodiumChunkReady(ClientLevel world, int chunkX, int chunkZ) {
        try {
            if (!sodiumLookupDone) {
                sodiumLookupDone = true;
                Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
                Class<?> tracker = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker");
                sodiumTrackerLookup = holder.getMethod("get", ClientLevel.class);
                sodiumChunkStatus = tracker.getMethod("onChunkStatusAdded", int.class, int.class, int.class);
            }
            if (sodiumTrackerLookup != null && sodiumChunkStatus != null) {
                Object tracker = sodiumTrackerLookup.invoke(null, world);
                sodiumChunkStatus.invoke(tracker, chunkX, chunkZ, 2);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Sodium is optional; vanilla chunk readiness remains authoritative.
            sodiumTrackerLookup = null;
            sodiumChunkStatus = null;
        }
    }

    private static int terrainHeight(int x, int z) {
        double broad = Math.sin(x * 0.045) * 2.8 + Math.cos(z * 0.052) * 2.4;
        double detail = Math.sin((x + z) * 0.13) * 0.7 + Math.cos((x - z) * 0.11) * 0.55;
        double clearing = Math.max(0.0, 1.0 - Math.sqrt(x * x + (z + 4) * (z + 4)) / 22.0);
        return (int) Math.round(63.0 + broad + detail - clearing * 1.5);
    }

    private static void growTree(LevelChunkSection[] sections, int minSection, int minY,
                                 int x, int baseY, int z) {
        for (int y = 0; y < 5; y++)
            put(sections, minSection, minY, x, baseY + y, z, Blocks.OAK_LOG.defaultBlockState());
        for (int dy = 2; dy <= 5; dy++) {
            int radius = dy >= 4 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius * 2) continue;
                    put(sections, minSection, minY, x + dx, baseY + dy, z + dz,
                            Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
        }
    }

    private static void put(LevelChunkSection[] sections, int minSection, int minY,
                            int x, int y, int z, BlockState state) {
        int sectionY = Math.floorDiv(y, 16);
        int index = sectionY - minSection;
        if (index < 0 || index >= sections.length || x < 0 || x >= 16 || z < 0 || z >= 16) return;
        sections[index].setBlockState(x, Math.floorMod(y, 16), z, state);
    }

    private static void destroy() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        if (level != null) {
            try {
                mc.levelRenderer.setLevel(mc.level);
                mc.gameRenderer.setLevel(mc.level);
                level.close();
            } catch (Throwable error) {
                java.util.logging.Logger.getLogger("VantaClient").log(java.util.logging.Level.WARNING, "Failed to release the Vanta title world cleanly", error);
            }
        }
        level = null;
        listener = null;
        gameMode = null;
        cameraRig = null;
    }
}
