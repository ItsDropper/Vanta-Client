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
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.stats.StatsCounter;

import java.util.List;

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
        try {
            mc.level = level;
            mc.player = cameraRig;
            mc.gameMode = gameMode;
            mc.setCameraEntity(cameraRig);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
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
            mc.options.setCameraType(previousCameraType);
            mc.setCameraEntity(previousCamera);
        } finally {
            previousLevel = null;
            previousPlayer = null;
            previousGameMode = null;
            previousCamera = null;
            previousCameraType = null;
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
        mc.levelRenderer.setLevel(level);
        mc.gameRenderer.setLevel(level);
    }

    private static RegistryAccess.Frozen loadRegistries() throws Exception {
        try (MultiPackResourceManager resources = new MultiPackResourceManager(
                PackType.SERVER_DATA, List.<PackResources>of(ServerPacksSource.createVanillaPackSource()))) {
            RegistryAccess.Frozen layers = RegistryLayer.createRegistryAccess();
            var staticTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
            var worldLayer = RegistryLayer.WORLDGEN;
            var worldContext = layers.getAccessForLoading(worldLayer);
            List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(worldContext, staticTags);
            var world = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
            var dimensionContext = new java.util.ArrayList<>(lookups);
            dimensionContext.addAll(world.listRegistries().toList());
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
                        int ground = terrainHeight(wx, wz);
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
                        if (ground > 62 && Math.floorMod(wx * 31 + wz * 17, 137) == 0) {
                            growTree(sections, minSection, chunkMinY, x, ground + 1, z);
                        } else if (ground >= 62 && Math.floorMod(wx * 19 + wz * 13, 43) == 0) {
                            put(sections, minSection, chunkMinY, x, ground + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                        }
                    }
                }
                ChunkPos pos = new ChunkPos(cx, cz);
                LevelChunk chunk = new LevelChunk(world, pos, UpgradeData.EMPTY,
                        new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, sections, null, null);
                Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
                ClientboundLevelChunkPacketData packet = new ClientboundLevelChunkPacketData(chunk);
                world.chunkSource.replaceWithPacketData(cx, cz, packet);
            }
        }
        for (int pass = 0; world.getLightEngine().hasLightWork() && pass < 64; pass++) {
            world.getLightEngine().runLightUpdates();
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
