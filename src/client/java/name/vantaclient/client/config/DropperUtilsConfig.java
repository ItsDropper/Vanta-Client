package name.vantaclient.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class DropperUtilsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("dropperutils.json").toFile();

    public int configVersion = 19;

    public boolean armorHud = false;
    public boolean totemCounter = false;
    public boolean fullbright = true;
    public boolean saturation = true;
    public boolean zoom = true;
    public boolean motionBlur = false;
    public boolean smoothZoom = true;
    public boolean anchorOptimizer = false;
    public boolean debugHud = false;
    public boolean noExplosionEffects = false;
    public boolean lowFire = false;
    public boolean potionEffects = false;
    public boolean hudEditor = false;
    public boolean armorHudHorizontal = true;
    public boolean fpsHud = true;
    public boolean pingHud = true;
    public boolean keystrokesHud = false;
    public boolean cpsHud = false;
    public boolean sprintToggleEnabled = false;
    public boolean sneakToggleEnabled = false;
    public boolean chatTimestamps = false;
    public String chatTimestampFormat = "HH:mm";
    public boolean performanceOptimizer = false;
    public boolean adaptiveEntityDistance = false;
    /** Fraction of the user's configured entity draw distance retained under load. */
    public float minimumEntityDistanceRatio = 0.65f;
    public boolean farViewEnabled = false;
    public boolean farViewAdaptive = false;
    public boolean vanillaHud = false;
    public boolean flyBoost = false;
    public boolean ogAnimations = false;
    public boolean nametagHidePlayers = false;
    public boolean nametagUtils = false;
    public boolean crashPatchDiagnostics = false;
    public boolean disableDoubleTapSprint = false;

    public float flyBoostMultiplier = 1.5f;
    public float nametagScale = 1.0f;
    public String ogAnimationPreset = "CLASSIC_1_8";
    public int sprintWindowBeforeDisable = 7;
    public boolean sprintWindowCaptured = false;

    public float fpsHudX = 0.02f;
    public float fpsHudY = 0.34f;
    public float pingHudX = 0.02f;
    public float pingHudY = 0.38f;
    public float keystrokesHudX = 0.02f;
    public float keystrokesHudY = 0.48f;
    public float cpsHudX = 0.02f;
    public float cpsHudY = 0.42f;
    public float fpsHudScale = 1.0f;
    public float pingHudScale = 1.0f;
    public float keystrokesHudScale = 1.0f;
    public float cpsHudScale = 1.0f;

    public int zoomFov = 30;
    public float zoomSpeed = 0.15f;
    public float motionBlurStrength = 0.60f;
    public int motionBlurSamples = 6;
    public float motionBlurSensitivity = 1.0f;
    public float lowFireHeight = 0.5f;
    public float fullbrightBrightness = 100.0f;
    public float fullbrightMinimum = 0.0f;
    public int anchorScanInterval = 40;
    public int anchorScanRange = 3;
    public float zoomScrollStrength = 0.65f;
    public float motionBlurIntensity = 1.0f;
    public boolean lowFireHideOverlay = false;
    public boolean noExplosionParticles = true;
    public boolean noExplosionSmoke = true;
    public int anchorPredictionTimeoutTicks = 40;
    public float anchorPredictionRange = 12.0f;
    public boolean anchorExplosionPreview = true;
    /** Vanilla close-detail distance. Distant terrain is rendered by FarTerrainRenderer. */
    public int farViewRenderDistance = 24;
    /** Maximum singleplayer LOD range, in chunks. */
    public int farViewDistance = 2048;
    public String farViewQuality = "BALANCED";

    public float armorHudScale = 1.0f;
    public float debugHudScale = 1.0f;
    public float potionHudScale = 1.0f;
    public float totemCounterScale = 1.0f;
    public float performanceHudX = 0.02f;
    public float performanceHudY = 0.10f;
    public float performanceHudScale = 1.0f;

    public float debugHudX = 0.01f;
    public float debugHudY = 0.30f;
    public float armorHudX = 0.02f;
    public float armorHudY = 0.20f;
    public float totemHudX = 0.30f;
    public float totemHudY = 0.20f;
    public float potionHudX = 0.01f;
    public float potionHudY = 0.02f;

    private static DropperUtilsConfig INSTANCE;

    public static DropperUtilsConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public static void load() {
        try {
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE)) {
                    com.google.gson.JsonObject json = GSON.fromJson(reader,
                            com.google.gson.JsonObject.class);
                    boolean legacyConfig = !json.has("configVersion");
                    INSTANCE = GSON.fromJson(json, DropperUtilsConfig.class);
                    if (legacyConfig) INSTANCE.configVersion = 1;
                }

                if (INSTANCE.configVersion < 2) migrateV2();
                if (INSTANCE.configVersion < 3) migrateV3();
                if (INSTANCE.configVersion < 4) migrateV4();
                if (INSTANCE.configVersion < 5) migrateV5();
                if (INSTANCE.configVersion < 6) migrateV6();
                if (INSTANCE.configVersion < 7) migrateV7();
                if (INSTANCE.configVersion < 8) migrateV8();
                if (INSTANCE.configVersion < 9) migrateV9();
                if (INSTANCE.configVersion < 10) migrateV10();
                if (INSTANCE.configVersion < 11) migrateV11();
                if (INSTANCE.configVersion < 12) migrateV12();
                if (INSTANCE.configVersion < 13) migrateV13();
                if (INSTANCE.configVersion < 14) migrateV14();
                if (INSTANCE.configVersion < 15) migrateV15();
                if (INSTANCE.configVersion < 16) migrateV16();
                if (INSTANCE.configVersion < 17) migrateV17();
                if (INSTANCE.configVersion < 18) migrateV18();
                if (INSTANCE.configVersion < 19) migrateV19();
                fixBrokenHudPositions();
                sanitizeHudConfiguration();
            } else {
                INSTANCE = new DropperUtilsConfig();
                save();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            INSTANCE = new DropperUtilsConfig();
        }
    }

    private static void migrateV2() {
        System.out.println("[DropperUtils] Migrating config v1 -> v2");
        convertHudPositions();
        INSTANCE.configVersion = 2;
        save();
    }

    private static void migrateV3() {
        System.out.println("[DropperUtils] Migrating config v2 -> v3");
        convertHudPositions();
        INSTANCE.configVersion = 3;
        save();
    }

    private static void migrateV4() {
        System.out.println("[DropperUtils] Migrating config v3 -> v4");
        if (INSTANCE.hudEditor) INSTANCE.hudEditor = false;
        if (!INSTANCE.armorHudHorizontal) INSTANCE.armorHudHorizontal = true;
        INSTANCE.configVersion = 4;
        save();
    }

    private static void migrateV5() {
        System.out.println("[DropperUtils] Migrating config v4 -> v5");
        INSTANCE.configVersion = 5;
        save();
    }

    private static void migrateV6() {
        System.out.println("[DropperUtils] Migrating config v5 -> v6");
        INSTANCE.configVersion = 6;
        save();
    }

    private static void migrateV7() {
        System.out.println("[DropperUtils] Migrating config v6 -> v7");
        INSTANCE.configVersion = 7;
        save();
    }

    private static void migrateV8() {
        System.out.println("[DropperUtils] Migrating config v7 -> v8");
        INSTANCE.configVersion = 8;
        save();
    }

    private static void migrateV9() {
        System.out.println("[DropperUtils] Migrating config v8 -> v9");
        INSTANCE.configVersion = 9;
        save();
    }

    private static void migrateV10() {
        System.out.println("[DropperUtils] Migrating config v9 -> v10");
        INSTANCE.configVersion = 10;
        save();
    }

    private static void migrateV11() {
        System.out.println("[DropperUtils] Migrating config v10 -> v11");
        INSTANCE.farViewDistance = 2048;
        INSTANCE.configVersion = 11;
        save();
    }

    private static void migrateV12() {
        System.out.println("[DropperUtils] Migrating config v11 -> v12");
        INSTANCE.vanillaHud = false;
        INSTANCE.flyBoost = false;
        INSTANCE.flyBoostMultiplier = 1.5f;
        INSTANCE.configVersion = 12;
        save();
    }

    private static void migrateV13() {
        System.out.println("[DropperUtils] Migrating config v12 -> v13");
        INSTANCE.ogAnimations = false;
        INSTANCE.ogAnimationPreset = "CLASSIC_1_8";
        INSTANCE.configVersion = 13;
        save();
    }

    private static void migrateV14() {
        System.out.println("[DropperUtils] Migrating config v13 -> v14");
        INSTANCE.disableDoubleTapSprint = false;
        INSTANCE.sprintWindowBeforeDisable = 7;
        INSTANCE.sprintWindowCaptured = false;
        INSTANCE.configVersion = 14;
        save();
    }

    private static void migrateV15() {
        System.out.println("[DropperUtils] Migrating config v14 -> v15");
        INSTANCE.chatTimestampFormat = "HH:mm";
        INSTANCE.configVersion = 15;
        save();
    }

    private static void migrateV16() {
        System.out.println("[DropperUtils] Migrating config v15 -> v16");
        INSTANCE.nametagScale = 1.0f;
        INSTANCE.nametagHidePlayers = false;
        INSTANCE.configVersion = 16;
        save();
    }

    private static void migrateV17() {
        System.out.println("[DropperUtils] Migrating config v16 -> v17");
        INSTANCE.crashPatchDiagnostics = false;
        INSTANCE.configVersion = 17;
        save();
    }

    private static void migrateV18() {
        System.out.println("[DropperUtils] Migrating config v17 -> v18");
        INSTANCE.nametagUtils = false;
        INSTANCE.configVersion = 18;
        save();
    }

    private static void migrateV19() {
        System.out.println("[DropperUtils] Migrating config v18 -> v19");
        INSTANCE.adaptiveEntityDistance = false;
        INSTANCE.minimumEntityDistanceRatio = 0.65f;
        INSTANCE.configVersion = 19;
        save();
    }

    private static void fixBrokenHudPositions() {
        boolean changed = false;
        if (INSTANCE.armorHudX > 1) {
            INSTANCE.armorHudX /= 1000f;
            INSTANCE.armorHudY /= 1000f;
            changed = true;
        }
        if (INSTANCE.totemHudX > 1) {
            INSTANCE.totemHudX /= 1000f;
            INSTANCE.totemHudY /= 1000f;
            changed = true;
        }
        if (INSTANCE.potionHudX > 1) {
            INSTANCE.potionHudX /= 1000f;
            INSTANCE.potionHudY /= 1000f;
            changed = true;
        }
        if (INSTANCE.debugHudX > 1) {
            INSTANCE.debugHudX /= 1000f;
            INSTANCE.debugHudY /= 1000f;
            changed = true;
        }
        if (changed) {
            System.out.println("[DropperUtils] Fixed broken HUD positions");
            save();
        }
    }


    /**
     * Keeps persisted HUD geometry within normalized viewport and rendering bounds.
     * Legacy coordinate migrations run first so their thousandths-based values are preserved.
     */
    private static void sanitizeHudConfiguration() {
        boolean changed = false;
        float minimumEntityRatio = Float.isFinite(INSTANCE.minimumEntityDistanceRatio)
                ? Math.max(0.5f, Math.min(1.0f, INSTANCE.minimumEntityDistanceRatio))
                : 0.65f;
        if (Float.compare(minimumEntityRatio, INSTANCE.minimumEntityDistanceRatio) != 0) {
            INSTANCE.minimumEntityDistanceRatio = minimumEntityRatio;
            changed = true;
        }

        changed |= sanitizePosition("fpsHudX", INSTANCE.fpsHudX, 0.02f);
        INSTANCE.fpsHudX = normalizedPosition(INSTANCE.fpsHudX, 0.02f);
        changed |= sanitizePosition("fpsHudY", INSTANCE.fpsHudY, 0.34f);
        INSTANCE.fpsHudY = normalizedPosition(INSTANCE.fpsHudY, 0.34f);
        changed |= sanitizePosition("pingHudX", INSTANCE.pingHudX, 0.02f);
        INSTANCE.pingHudX = normalizedPosition(INSTANCE.pingHudX, 0.02f);
        changed |= sanitizePosition("pingHudY", INSTANCE.pingHudY, 0.38f);
        INSTANCE.pingHudY = normalizedPosition(INSTANCE.pingHudY, 0.38f);
        changed |= sanitizePosition("keystrokesHudX", INSTANCE.keystrokesHudX, 0.02f);
        INSTANCE.keystrokesHudX = normalizedPosition(INSTANCE.keystrokesHudX, 0.02f);
        changed |= sanitizePosition("keystrokesHudY", INSTANCE.keystrokesHudY, 0.48f);
        INSTANCE.keystrokesHudY = normalizedPosition(INSTANCE.keystrokesHudY, 0.48f);
        changed |= sanitizePosition("cpsHudX", INSTANCE.cpsHudX, 0.02f);
        INSTANCE.cpsHudX = normalizedPosition(INSTANCE.cpsHudX, 0.02f);
        changed |= sanitizePosition("cpsHudY", INSTANCE.cpsHudY, 0.42f);
        INSTANCE.cpsHudY = normalizedPosition(INSTANCE.cpsHudY, 0.42f);
        changed |= sanitizePosition("performanceHudX", INSTANCE.performanceHudX, 0.02f);
        INSTANCE.performanceHudX = normalizedPosition(INSTANCE.performanceHudX, 0.02f);
        changed |= sanitizePosition("performanceHudY", INSTANCE.performanceHudY, 0.10f);
        INSTANCE.performanceHudY = normalizedPosition(INSTANCE.performanceHudY, 0.10f);
        changed |= sanitizePosition("debugHudX", INSTANCE.debugHudX, 0.01f);
        INSTANCE.debugHudX = normalizedPosition(INSTANCE.debugHudX, 0.01f);
        changed |= sanitizePosition("debugHudY", INSTANCE.debugHudY, 0.30f);
        INSTANCE.debugHudY = normalizedPosition(INSTANCE.debugHudY, 0.30f);
        changed |= sanitizePosition("armorHudX", INSTANCE.armorHudX, 0.02f);
        INSTANCE.armorHudX = normalizedPosition(INSTANCE.armorHudX, 0.02f);
        changed |= sanitizePosition("armorHudY", INSTANCE.armorHudY, 0.20f);
        INSTANCE.armorHudY = normalizedPosition(INSTANCE.armorHudY, 0.20f);
        changed |= sanitizePosition("totemHudX", INSTANCE.totemHudX, 0.30f);
        INSTANCE.totemHudX = normalizedPosition(INSTANCE.totemHudX, 0.30f);
        changed |= sanitizePosition("totemHudY", INSTANCE.totemHudY, 0.20f);
        INSTANCE.totemHudY = normalizedPosition(INSTANCE.totemHudY, 0.20f);
        changed |= sanitizePosition("potionHudX", INSTANCE.potionHudX, 0.01f);
        INSTANCE.potionHudX = normalizedPosition(INSTANCE.potionHudX, 0.01f);
        changed |= sanitizePosition("potionHudY", INSTANCE.potionHudY, 0.02f);
        INSTANCE.potionHudY = normalizedPosition(INSTANCE.potionHudY, 0.02f);

        changed |= sanitizeScale("fpsHudScale", INSTANCE.fpsHudScale);
        INSTANCE.fpsHudScale = normalizedScale(INSTANCE.fpsHudScale);
        changed |= sanitizeScale("pingHudScale", INSTANCE.pingHudScale);
        INSTANCE.pingHudScale = normalizedScale(INSTANCE.pingHudScale);
        changed |= sanitizeScale("keystrokesHudScale", INSTANCE.keystrokesHudScale);
        INSTANCE.keystrokesHudScale = normalizedScale(INSTANCE.keystrokesHudScale);
        changed |= sanitizeScale("cpsHudScale", INSTANCE.cpsHudScale);
        INSTANCE.cpsHudScale = normalizedScale(INSTANCE.cpsHudScale);
        changed |= sanitizeScale("performanceHudScale", INSTANCE.performanceHudScale);
        INSTANCE.performanceHudScale = normalizedScale(INSTANCE.performanceHudScale);
        changed |= sanitizeScale("debugHudScale", INSTANCE.debugHudScale);
        INSTANCE.debugHudScale = normalizedScale(INSTANCE.debugHudScale);
        changed |= sanitizeScale("armorHudScale", INSTANCE.armorHudScale);
        INSTANCE.armorHudScale = normalizedScale(INSTANCE.armorHudScale);
        changed |= sanitizeScale("totemCounterScale", INSTANCE.totemCounterScale);
        INSTANCE.totemCounterScale = normalizedScale(INSTANCE.totemCounterScale);
        changed |= sanitizeScale("potionHudScale", INSTANCE.potionHudScale);
        INSTANCE.potionHudScale = normalizedScale(INSTANCE.potionHudScale);

        if (changed) save();
    }

    private static float normalizedPosition(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : fallback;
    }

    private static float normalizedScale(float value) {
        return Float.isFinite(value) ? Math.max(0.25f, Math.min(3.0f, value)) : 1.0f;
    }

    private static boolean sanitizePosition(String name, float value, float fallback) {
        if (Float.compare(value, normalizedPosition(value, fallback)) == 0) return false;
        System.out.println("[DropperUtils] Normalized " + name + " in HUD config");
        return true;
    }

    private static boolean sanitizeScale(String name, float value) {
        if (Float.compare(value, normalizedScale(value)) == 0) return false;
        System.out.println("[DropperUtils] Normalized " + name + " in HUD config");
        return true;
    }

    private static void convertHudPositions() {
        INSTANCE.armorHudX /= 1000f;
        INSTANCE.armorHudY /= 1000f;
        INSTANCE.totemHudX /= 1000f;
        INSTANCE.totemHudY /= 1000f;
        INSTANCE.potionHudX /= 1000f;
        INSTANCE.potionHudY /= 1000f;
        INSTANCE.debugHudX /= 1000f;
        INSTANCE.debugHudY /= 1000f;
    }
}
