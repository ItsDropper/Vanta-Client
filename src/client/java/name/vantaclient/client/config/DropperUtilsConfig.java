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

    public int configVersion = 9;

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
    public boolean performanceOptimizer = false;

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
                fixBrokenHudPositions();
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