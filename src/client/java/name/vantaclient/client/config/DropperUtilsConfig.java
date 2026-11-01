package name.vantaclient.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class DropperUtilsConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final File FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("dropperutils.json")
            .toFile();


    public int configVersion = 4;


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


    public int zoomFov = 30;
    public float zoomSpeed = 0.15f;
    public float motionBlurStrength = 0.60f;
    public int motionBlurSamples = 6;
    public float motionBlurSensitivity = 1.0f;
    public float lowFireHeight = 0.5f;
    public float fullbrightBrightness = 100.0f;
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

        if (INSTANCE == null) {
            load();
        }

        return INSTANCE;
    }



    public static void save() {

        try (FileWriter writer = new FileWriter(FILE)) {

            GSON.toJson(INSTANCE, writer);

        } catch (Exception e) {

            e.printStackTrace();

        }
    }



    public static void load() {

        try {

            if (FILE.exists()) {

                FileReader reader = new FileReader(FILE);

                com.google.gson.JsonObject json =
                        GSON.fromJson(reader, com.google.gson.JsonObject.class);

                reader.close();

                boolean legacyConfig = !json.has("configVersion");

                INSTANCE = GSON.fromJson(
                        json,
                        DropperUtilsConfig.class
                );

                if (legacyConfig) {
                    INSTANCE.configVersion = 1;
                }

                if (INSTANCE.configVersion < 2) {
                    migrateV2();
                }

                if (INSTANCE.configVersion < 3) {
                    migrateV3();
                }

                if (INSTANCE.configVersion < 4) {
                    migrateV4();
                }

                fixBrokenHudPositions();

            } else {

                INSTANCE = new DropperUtilsConfig();
                save();

            }

        } catch (Exception e) {

            e.printStackTrace();
            INSTANCE = new DropperUtilsConfig();

        }
    }



    private static void migrateV2() {

        System.out.println(
                "[DropperUtils] Migrating config v1 -> v2"
        );


        convertHudPositions();

        INSTANCE.configVersion = 2;

        save();
    }



    private static void migrateV3() {

        System.out.println(
                "[DropperUtils] Migrating config v2 -> v3"
        );


        convertHudPositions();

        INSTANCE.configVersion = 3;

        save();
    }

    private static void migrateV4() {

        System.out.println(
                "[DropperUtils] Migrating config v3 -> v4"
        );

        if (INSTANCE.hudEditor) {
            INSTANCE.hudEditor = false;
        }

        if (!INSTANCE.armorHudHorizontal) {
            INSTANCE.armorHudHorizontal = true;
        }

        INSTANCE.configVersion = 4;

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

            System.out.println(
                    "[DropperUtils] Fixed broken HUD positions"
            );

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