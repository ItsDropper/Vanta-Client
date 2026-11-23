package name.vantaclient.client.gui.lobby;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import name.vantaclient.mixin.CameraAccessor;
import name.vantaclient.mixin.LivingEntityAccessor;
import name.vantaclient.mixin.ConnectionAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.stats.StatsCounter;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.util.Util;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import java.util.List;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Owns an isolated, generated ClientLevel for the title screen.
 *
 * The level is only swapped into Minecraft while GameRenderer is rendering.
 * The actual menu screen, input routing and title-screen lifecycle remain owned
 * by vanilla. No panorama texture is involved in this scene.
 */
public final class VantaLobbyWorld {
    private static final int VIEW_DISTANCE = 7;
    // Keep a one-chunk safety ring outside the rendered view and two extra
    // cache slots so edge sections can compile with their neighbors available.
    private static final int CHUNK_RADIUS = VIEW_DISTANCE + 1;
    private static final int CHUNK_CACHE_DISTANCE = VIEW_DISTANCE + 2;
    private static final int CENTER_X = 0;
    private static final int CENTER_Z = 0;

    private static ClientLevel level;
    private static ClientPacketListener listener;
    private static MultiPlayerGameMode gameMode;
    private static LocalPlayer cameraRig;
    private static VantaLobbyAvatar avatar;
    private static boolean active;
    private static long nextStartAttemptNanos;
    private static boolean terrainRendered;
    private static long firstTerrainFrameNanos;
    private static final long TERRAIN_WARMUP_NANOS = 2_500_000_000L;
    private static boolean swapped;
    private static ClientLevel previousLevel;
    private static LocalPlayer previousPlayer;
    private static MultiPlayerGameMode previousGameMode;
    private static Entity previousCamera;
    private static net.minecraft.client.CameraType previousCameraType;
    private static int previousServerRenderDistance;
    private static float previousCameraEyeHeight;
    private static float previousCameraEyeHeightOld;
    private static boolean previousCameraEyeHeightSaved;
    private static boolean sodiumLookupDone;
    private static Method sodiumTrackerLookup;
    private static Method sodiumChunkStatus;
    private static boolean sodiumRendererLookupDone;
    private static Method sodiumRendererLookup;
    private static Method sodiumVisibleChunkCount;
    private static boolean vulkanLookupDone;
    private static Object vulkanChunkStatusMap;
    private static Method vulkanChunkStatus;

    private VantaLobbyWorld() {}

    public static boolean isActive() {
        return active && level != null;
    }

    /** GameRenderer's renderLevel argument is computed before beginFrame swaps in the synthetic level. */
    public static boolean shouldRenderTitleWorld() {
        return isActive() && Minecraft.getInstance().screen instanceof TitleScreen;
    }

    /** The panorama may only be removed after the world renderer has produced terrain meshes. */
    public static boolean isWorldReadyForDisplay() {
        return active && level != null && terrainRendered;
    }

    /** Called after LevelRenderer.renderLevel has completed for the lobby level. */
    private static long lastDiagnosticNanos;

    private static void diagnose(String where, Minecraft mc) {
        long now = System.nanoTime();
        if (now - lastDiagnosticNanos < 2_000_000_000L) return;
        lastDiagnosticNanos = now;
        int sections = -1;
        try { sections = mc.levelRenderer.countRenderedSections(); } catch (Throwable ignored) { }
        java.util.logging.Logger.getLogger("VantaClient").info("[lobby-diag] " + where
                + " active=" + active + " swapped=" + swapped + " mcLevelIsLobby=" + (mc.level == level)
                + " sections=" + sections + " rendered=" + terrainRendered
                + " avatarInLevel=" + (avatar != null && level != null && level.getEntity(avatar.getId()) != null)
                + " screen=" + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName()));
    }

    public static void markWorldRendered() {
        diagnose("renderLevel-tail", Minecraft.getInstance());
        if (!active || level == null || Minecraft.getInstance().level != level || terrainRendered) return;
        int renderedSections = getVisibleTerrainSectionCount();
        if (renderedSections <= 0) return;

        long now = System.nanoTime();
        if (firstTerrainFrameNanos == 0L) {
            firstTerrainFrameNanos = now;
            java.util.logging.Logger.getLogger("VantaClient").info(
                    "Vanta title world began producing terrain (" + renderedSections + " visible sections)");
            return;
        }

        // The chunk renderer compiles the injected ClientLevel asynchronously. A non-zero
        // visible count only proves that the first section arrived; removing the panorama
        // at that point exposes a mostly empty sky while the remaining scene is still compiling.
        // Keep the fallback panorama until the initial chunk compilation has had time to settle.
        long warmupNanos = now - firstTerrainFrameNanos;
        if (warmupNanos >= TERRAIN_WARMUP_NANOS) {
            terrainRendered = true;
            java.util.logging.Logger.getLogger("VantaClient").info(
                    "Vanta title world is ready (" + renderedSections + " visible sections, "
                            + (warmupNanos / 1_000_000L) + " ms warmup)");
        }
    }

    /** Sodium replaces vanilla's visible-section list, so query its public renderer API when present. */
    private static int getVisibleTerrainSectionCount() {
        try {
            if (!sodiumRendererLookupDone) {
                sodiumRendererLookupDone = true;
                Class<?> renderer = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
                sodiumRendererLookup = renderer.getMethod("instanceNullable");
                sodiumVisibleChunkCount = renderer.getMethod("getVisibleChunkCount");
            }
            if (sodiumRendererLookup != null && sodiumVisibleChunkCount != null) {
                Object renderer = sodiumRendererLookup.invoke(null);
                if (renderer != null) {
                    Object count = sodiumVisibleChunkCount.invoke(renderer);
                    if (count instanceof Number number && number.intValue() > 0) return number.intValue();
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            sodiumRendererLookup = null;
            sodiumVisibleChunkCount = null;
        }
        return Minecraft.getInstance().levelRenderer.countRenderedSections();
    }

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (active || mc.level != null || !(mc.screen instanceof TitleScreen)) return;
        if (name.vantaclient.client.config.VantaClientPreferences.get().titleStyle
                == name.vantaclient.client.config.VantaClientPreferences.TitleStyle.VANILLA) return;
        long now = System.nanoTime();
        if (now < nextStartAttemptNanos) return;
        // TitleScreen.init can run while Minecraft is still transitioning out of
        // startup/world teardown. Retry from the render loop rather than making
        // successful lobby creation depend on that single lifecycle callback.
        nextStartAttemptNanos = now + 250_000_000L;
        try {
            build();
            // Terrain generation can take long enough to consume most of the opening move.
            // Start the camera clock only after the isolated ClientLevel and its chunks exist.
            VantaLobbyScene.restartCameraIntro();
            terrainRendered = false;
            firstTerrainFrameNanos = 0L;
            active = true;
            nextStartAttemptNanos = 0L;
        } catch (Throwable error) {
            destroy();
            java.util.logging.Logger.getLogger("VantaClient").log(java.util.logging.Level.SEVERE, "Unable to create the Vanta title world", error);
        }
    }

    /**
     * Advances the title world's per-tick systems (light texture, environment attributes, block
     * entities, world time) the way a joined world does. Without it the first frames of a fresh
     * client render with uninitialised sky and light state.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active || level == null || swapped || mc.level != null || !(mc.screen instanceof TitleScreen)) return;
        if (++timeTicks >= 100) {
            timeTicks = 0;
            level.setTimeFromServer(0L, lobbyDayTime(), false);
        }
        beginFrame();
        if (!swapped) return;
        try {
            // A sparse layer of real world-space cherry petals gives the camera a
            // foreground depth cue; unlike GUI dust, these particles move through
            // the generated level and inherit its camera perspective and lighting.
            if (avatar != null
                    && name.vantaclient.client.config.VantaClientPreferences.get().animations
                    && ++ambientParticleTicks >= 3) {
                ambientParticleTicks = 0;
                ThreadLocalRandom random = ThreadLocalRandom.current();
                // Keep most petals between the camera and avatar so they read as
                // foreground motion instead of disappearing into the distant tree line.
                double x = avatar.getX() + random.nextDouble(-5.5, 5.5);
                double y = 65.5 + random.nextDouble(0.5, 6.5);
                double z = avatar.getZ() + random.nextDouble(1.0, 7.0);
                level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y, z,
                        random.nextDouble(-0.010, 0.010),
                        random.nextDouble(-0.012, -0.003),
                        random.nextDouble(-0.012, 0.006));
            }
            // The real Minecraft tick skips ParticleEngine while the client has no
            // active world. The lobby swaps its ClientLevel only for rendering/ticking,
            // so advance the particle queue here as part of the isolated world tick.
            mc.particleEngine.tick();
            mc.gameRenderer.tick();
            level.tickBlockEntities();
            level.tick(() -> true);
        } catch (Throwable error) {
            if (!tickFailureLogged) {
                tickFailureLogged = true;
                java.util.logging.Logger.getLogger("VantaClient").log(java.util.logging.Level.WARNING,
                        "Vanta title world tick failed", error);
            }
        } finally {
            restore();
        }
    }

    private static boolean tickFailureLogged;
    private static int timeTicks;
    private static int ambientParticleTicks;

    /** Day time following the player's wall clock; night is held at dusk/dawn so the scene stays readable. */
    private static long lobbyDayTime() {
        java.time.LocalTime now = java.time.LocalTime.now();
        double hours = now.getHour() + now.getMinute() / 60.0;
        long ticks = Math.floorMod((long) (hours * 1000.0) + 18000L, 24000L);
        if (ticks >= 13000L && ticks < 23000L) ticks = ticks < 18000L ? 13000L : 23000L;
        return ticks;
    }

    public static void stop() {
        restore();
        nextStartAttemptNanos = 0L;
        destroy();
    }

    /** Called at GameRenderer.render HEAD; restores the real client state at RETURN. */
    public static void beginFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (active) diagnose("beginFrame", mc);
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
        Camera renderCamera = mc.gameRenderer.getMainCamera();
        CameraAccessor cameraAccessor = (CameraAccessor) renderCamera;
        previousCameraEyeHeight = cameraAccessor.vanta$getEyeHeight();
        previousCameraEyeHeightOld = cameraAccessor.vanta$getEyeHeightOld();
        previousCameraEyeHeightSaved = true;
        swapped = true;
        try {
            VantaLobbyCamera camera = VantaLobbyScene.camera();
            float yaw = 180.0f + camera.subjectYaw();
            float pitch = camera.subjectPitch();
            // The lobby rig is not ticked while the title screen is open. Keep its
            // previous and current transforms identical so Camera.setup() does not
            // interpolate from the constructor's origin/rotation on every frame.
            cameraRig.snapTo(cameraRig.getX(), cameraRig.getY(), cameraRig.getZ(), yaw, 0.0f);
            cameraRig.setYHeadRot(yaw);
            cameraRig.setYBodyRot(yaw);
            if (avatar != null) {
                // Face the visible model toward the lobby camera. The camera sits
                // on the +Z side of the avatar and looks back toward -Z.
                float avatarBodyYaw = camera.avatarBodyYaw();
                float avatarHeadYaw = camera.avatarHeadYaw();
                avatar.snapTo(avatar.getX(), 64.0 + camera.avatarBreathOffset(),
                        avatar.getZ(), avatarBodyYaw, 0.0f);
                avatar.setYHeadRot(avatarHeadYaw);
                avatar.setYBodyRot(avatarBodyYaw);
                LivingEntityAccessor avatarRotation = (LivingEntityAccessor) avatar;
                avatarRotation.vanta$setPreviousHeadYaw(avatarHeadYaw);
                avatarRotation.vanta$setPreviousBodyYaw(avatarBodyYaw);
            }
            LivingEntityAccessor rotationAccessor = (LivingEntityAccessor) cameraRig;
            // The lobby player is intentionally not ticked. Vanilla's player renderer
            // interpolates head/body yaw from their previous values; leaving those at
            // the constructor defaults makes the model snap back and forth every frame.
            rotationAccessor.vanta$setPreviousHeadYaw(yaw);
            rotationAccessor.vanta$setPreviousBodyYaw(yaw);
            mc.level = level;
            mc.player = cameraRig;
            mc.gameMode = gameMode;
            mc.setCameraEntity(cameraRig);
            cameraAccessor.vanta$setEyeHeight(cameraRig.getEyeHeight());
            cameraAccessor.vanta$setEyeHeightOld(cameraRig.getEyeHeight());
            mc.options.setServerRenderDistance(VIEW_DISTANCE);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        } catch (Throwable error) {
            restore();
            java.util.logging.Logger.getLogger("VantaClient").log(java.util.logging.Level.SEVERE, "Unable to activate the Vanta title world for rendering", error);
        }
    }

    /** Re-apply the lobby view after Camera.setup has selected the title camera. */
    public static void applyCameraRotation() {
        Minecraft mc = Minecraft.getInstance();
        if (!isActive() || mc.level != level || !swapped || cameraRig == null) return;
        VantaLobbyCamera camera = VantaLobbyScene.camera();
        CameraAccessor cameraAccessor = (CameraAccessor) mc.gameRenderer.getMainCamera();
        // Keep the avatar in the world while framing it with an independent, elevated
        // camera transform. Third-person mode enables the player renderer; the explicit
        // camera position avoids the vanilla fixed-distance third-person composition.
        // The avatar is at z=-5.5. Keep the camera on its +Z side and
        // explicitly face north (-Z); yaw 0 would look away from the avatar.
        cameraAccessor.vanta$setPosition(
                avatar.getX() + camera.cameraOffsetX(),
                avatar.getY() + 6.0 + camera.cameraOffsetY(),
                avatar.getZ() + 13.0 + camera.cameraOffsetZ());
        cameraAccessor.vanta$setRotation(
                180.0f + camera.sceneYaw(), 19.0f + camera.scenePitch());
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
            if (previousCameraEyeHeightSaved) {
                CameraAccessor cameraAccessor = (CameraAccessor) mc.gameRenderer.getMainCamera();
                cameraAccessor.vanta$setEyeHeight(previousCameraEyeHeight);
                cameraAccessor.vanta$setEyeHeightOld(previousCameraEyeHeightOld);
            }
        } finally {
            previousLevel = null;
            previousPlayer = null;
            previousGameMode = null;
            previousCamera = null;
            previousCameraType = null;
            previousServerRenderDistance = 0;
            previousCameraEyeHeight = 0.0f;
            previousCameraEyeHeightOld = 0.0f;
            previousCameraEyeHeightSaved = false;
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
        ((ConnectionAccessor) connection).vanta$setAddress(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        listener = new ClientPacketListener(mc, connection, cookie);
        Holder<?> dimension = registries.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        level = new ClientLevel(
                listener,
                new ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, false),
                Level.OVERWORLD,
                (Holder) dimension,
                CHUNK_CACHE_DISTANCE,
                2,
                mc.levelRenderer,
                false,
                0L,
                63
        );
        int configuredRenderDistance = mc.options.getEffectiveRenderDistance();
        mc.options.setServerRenderDistance(VIEW_DISTANCE);
        try {
            mc.levelRenderer.setLevel(level);
            mc.gameRenderer.setLevel(level);
            mc.particleEngine.setLevel(level);
        } finally {
            mc.options.setServerRenderDistance(configuredRenderDistance);
        }
        level.getChunkSource().updateViewCenter(CENTER_X, CENTER_Z);
        generateTerrain(level);
        // Chunk packet injection bypasses the normal network chunk-ready path. Recreate
        // the section view only after the generated chunks exist so every render section
        // starts dirty against real chunk data instead of an empty cache snapshot.
        mc.options.setServerRenderDistance(VIEW_DISTANCE);
        try {
            mc.levelRenderer.setLevel(level);
            mc.gameRenderer.setLevel(level);
            // replaceWithPacketData does not go through the normal chunk packet listener,
            // so explicitly dirty the complete render-section volume after the view area
            // is recreated. This guarantees the first compile sees the injected chunks.
            mc.levelRenderer.setSectionRangeDirty(
                    -VIEW_DISTANCE, level.getMinSectionY(), -VIEW_DISTANCE,
                    VIEW_DISTANCE, level.getMinSectionY() + level.getSectionsCount() - 1, VIEW_DISTANCE);
            // The section occlusion graph only walks chunks it was told are ready to render.
            // Vanilla announces that from the chunk packet handler, which the lobby bypasses.
            // Without this the terrain only appeared once a real world had populated the
            // graph's chunk set (i.e. after joining a world and returning to the menu).
            // PolyPlus does the same in LobbyCompat.requeueChunks.
            for (int cx = -CHUNK_RADIUS; cx <= CHUNK_RADIUS; cx++) {
                for (int cz = -CHUNK_RADIUS; cz <= CHUNK_RADIUS; cz++) {
                    var chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk != null) mc.levelRenderer.onChunkReadyToRender(chunk.getPos());
                }
            }
            mc.levelRenderer.needsUpdate();
        } finally {
            mc.options.setServerRenderDistance(configuredRenderDistance);
        }
        level.setTimeFromServer(0L, lobbyDayTime(), false);
        gameMode = new MultiPlayerGameMode(mc, listener);
        cameraRig = gameMode.createPlayer(level, new StatsCounter(), new ClientRecipeBook());
        cameraRig.noPhysics = true;
        cameraRig.setNoGravity(true);
        cameraRig.setInvisible(true);
        cameraRig.snapTo(0.5, 64.0, -5.5, 180.0f, 4.0f);
        cameraRig.setYHeadRot(180.0f);
        cameraRig.setYBodyRot(180.0f);
        cameraRig.setId(0x56414E54);
        level.addEntity(cameraRig);

        // Keep the camera/player controller separate from the visible avatar. PolyPlus
        // does this with a RemotePlayer subclass and an explicit SkinManager lookup;
        // a LocalPlayer is the wrong entity to use as the lobby's visible model.
        GameProfile profile = mc.getGameProfile();
        Supplier<PlayerSkin> skinLookup = () -> VantaLobbySkin.get(mc, profile);
        // The camera rig already uses the account UUID; the level rejects a second entity with
        // the same UUID, which silently dropped the visible avatar. Skin lookup keeps the real profile.
        GameProfile avatarProfile = new GameProfile(
                UUID.nameUUIDFromBytes(("vanta-lobby-avatar:" + profile.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                profile.name());
        avatar = new VantaLobbyAvatar(level, avatarProfile, skinLookup);
        avatar.noPhysics = true;
        avatar.setNoGravity(true);
        avatar.snapTo(0.5, 64.0, -5.5, 180.0f, 4.0f);
        avatar.setYHeadRot(180.0f);
        avatar.setYBodyRot(180.0f);
        avatar.setId(0x56414E55);
        level.addEntity(avatar);
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
        for (int cx = -CHUNK_RADIUS; cx <= CHUNK_RADIUS; cx++) {
            for (int cz = -CHUNK_RADIUS; cz <= CHUNK_RADIUS; cz++) {
                LevelChunkSection[] sections = new LevelChunkSection[sectionCount];
                for (int i = 0; i < sectionCount; i++) sections[i] = new LevelChunkSection(factory);
                VantaLobbyTerrain.generate(sections, minSection, cx, cz);
                ChunkPos pos = new ChunkPos(cx, cz);
                LevelChunk chunk = new LevelChunk(world, pos, UpgradeData.EMPTY,
                        new LevelChunkTicks(), new LevelChunkTicks(), 0L, sections, null, null);
                Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
                ClientboundLevelChunkPacketData packet = new ClientboundLevelChunkPacketData(chunk);
                LevelChunk loaded = world.getChunkSource().replaceWithPacketData(cx, cz, packet.getReadBuffer(), packet.getHeightmaps(),
                        packet.getBlockEntitiesTagsConsumer(cx, cz));
                if (loaded == null) {
                    throw new IllegalStateException("Client chunk cache rejected generated lobby chunk " + cx + "," + cz);
                }
                {
                    ChunkPos loadedPos = new ChunkPos(cx, cz);
                    // Match the native chunk-light initialization order: queue full sky light
                    // above the highest occupied section, publish section opacity, then enable
                    // the chunk so the light engine can propagate through the complete column.
                    int highestOccupiedSection = minSection - 1;
                    for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                        LevelChunkSection section = sections[sectionIndex];
                        world.getLightEngine().updateSectionStatus(
                                SectionPos.of(loadedPos, minSection + sectionIndex), section.hasOnlyAir());
                        if (!section.hasOnlyAir()) {
                            highestOccupiedSection = minSection + sectionIndex;
                        }
                    }
                    for (int skySection = highestOccupiedSection + 1;
                         skySection < world.getLightEngine().getMaxLightSection(); skySection++) {
                        world.getLightEngine().queueSectionData(LightLayer.SKY,
                                SectionPos.of(loadedPos, skySection), new DataLayer(15));
                    }
                    world.getLightEngine().setLightEnabled(loadedPos, true);

                }
            }
        }
        // Chunk packet injection bypasses the normal block-update lighting path.
        // Explicitly seed every emissive block so lanterns and other fixtures produce
        // block light in the synthetic level just as they do in a joined world.
        for (int cx = -CHUNK_RADIUS; cx <= CHUNK_RADIUS; cx++) {
            for (int cz = -CHUNK_RADIUS; cz <= CHUNK_RADIUS; cz++) {
                LevelChunk chunk = world.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 60; y <= 84; y++) {
                            BlockState state = chunk.getBlockState(new net.minecraft.core.BlockPos(
                                    cx * 16 + x, y, cz * 16 + z));
                            if (state.getLightEmission() > 0) {
                                world.getLightEngine().checkBlock(new net.minecraft.core.BlockPos(
                                        cx * 16 + x, y, cz * 16 + z));
                            }
                        }
                    }
                }
            }
        }
        for (int pass = 0; world.getLightEngine().hasLightWork() && pass < 768; pass++) {
            world.getLightEngine().runLightUpdates();
        }

        // Confirm every generated chunk remains resident and publish its light-ready
        // state to optional renderers that bypass vanilla's chunk packet listener.
        for (int cx = -CHUNK_RADIUS; cx <= CHUNK_RADIUS; cx++) {
            for (int cz = -CHUNK_RADIUS; cz <= CHUNK_RADIUS; cz++) {
                LevelChunk chunk = world.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    throw new IllegalStateException("Generated lobby chunk disappeared from cache at " + cx + "," + cz);
                }
                notifyChunkLightingReady(world, cx, cz);
            }
        }
    }

    private static void notifyChunkLightingReady(ClientLevel world, int chunkX, int chunkZ) {
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
        try {
            if (!vulkanLookupDone) {
                vulkanLookupDone = true;
                Class<?> map = Class.forName("net.vulkanmod.render.chunk.ChunkStatusMap");
                vulkanChunkStatusMap = map.getField("INSTANCE").get(null);
                vulkanChunkStatus = map.getMethod("setChunkStatus", int.class, int.class, byte.class);
            }
            if (vulkanChunkStatusMap != null && vulkanChunkStatus != null) {
                vulkanChunkStatus.invoke(vulkanChunkStatusMap, chunkX, chunkZ, (byte) 2);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            vulkanChunkStatusMap = null;
            vulkanChunkStatus = null;
        }
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
        mc.particleEngine.setLevel(mc.level);
        terrainRendered = false;
        firstTerrainFrameNanos = 0L;
        ambientParticleTicks = 0;
        level = null;
        listener = null;
        gameMode = null;
        cameraRig = null;
        avatar = null;
    }

    /**
     * Resolves the authenticated account texture property before creating the skin lookup.
     * Minecraft's launch GameProfile can occasionally be missing textures; PolyPlus
     * handles that by fetching the signed profile separately and retrying asynchronously.
     */
    private static final class VantaLobbySkin {
        private static final long[] RETRY_MILLIS = {5_000L, 30_000L, 120_000L, 600_000L};
        private static GameProfile resolvedProfile;
        private static UUID pendingId;
        private static boolean fetching;
        private static int attempt;
        private static long retryAt;
        private static GameProfile lookupProfile;
        private static Supplier<PlayerSkin> lookup;

        private static PlayerSkin get(Minecraft mc, GameProfile launchProfile) {
            UUID id = launchProfile.id();
            GameProfile profile;
            synchronized (VantaLobbySkin.class) {
                if (resolvedProfile != null && resolvedProfile.id().equals(id)) {
                    profile = resolvedProfile;
                } else if (launchProfile.properties().containsKey("textures")) {
                    resolvedProfile = launchProfile;
                    profile = launchProfile;
                } else {
                    requestProfile(mc, id);
                    profile = launchProfile;
                }

                if (lookup == null || lookupProfile != profile) {
                    lookupProfile = profile;
                    lookup = mc.getSkinManager().createLookup(profile, false);
                }
                return lookup.get();
            }
        }

        private static void requestProfile(Minecraft mc, UUID id) {
            long now = System.currentTimeMillis();
            if (!id.equals(pendingId)) {
                pendingId = id;
                attempt = 0;
                retryAt = 0L;
                fetching = false;
                resolvedProfile = null;
                lookupProfile = null;
                lookup = null;
            }
            if (fetching || now < retryAt) return;
            fetching = true;

            CompletableFuture.supplyAsync(() -> {
                try {
                    var result = mc.services().sessionService().fetchProfile(id, true);
                    return result == null ? null : result.profile();
                } catch (Throwable failure) {
                    java.util.logging.Logger.getLogger("VantaClient").log(
                            java.util.logging.Level.FINE,
                            "Could not resolve the authenticated lobby skin profile", failure);
                    return null;
                }
            }, Util.nonCriticalIoPool()).thenAccept(profile -> {
                synchronized (VantaLobbySkin.class) {
                    if (!id.equals(pendingId)) return;
                    fetching = false;
                    if (profile != null && id.equals(profile.id())
                            && profile.properties().containsKey("textures")) {
                        resolvedProfile = profile;
                        lookupProfile = null;
                        lookup = null;
                    } else {
                        long delay = RETRY_MILLIS[Math.min(attempt, RETRY_MILLIS.length - 1)];
                        attempt++;
                        retryAt = System.currentTimeMillis() + delay;
                    }
                }
            });
        }
    }

    /** Visible avatar with the same explicit account-skin lookup used by PolyPlus. */
    private static class VantaLobbyAvatar extends RemotePlayer {
        private final Supplier<PlayerSkin> skinLookup;

        private VantaLobbyAvatar(ClientLevel level, GameProfile profile,
                                  Supplier<PlayerSkin> skinLookup) {
            super(level, profile);
            this.skinLookup = skinLookup;
        }

        @Override
        public PlayerSkin getSkin() {
            return skinLookup.get();
        }
    }
}
