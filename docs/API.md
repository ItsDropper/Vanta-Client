# Vanta Client Integration API

The public API is currently **version 1** and targets the Minecraft 1.21.11 Fabric client.

## Optional integration

Vanta Client is not a required dependency for an integrating mod. Declare it as an optional suggestion in your own `fabric.mod.json`:

```json
{
  "suggests": {
    "dropperutils": "*"
  }
}
```

At runtime, check that the mod is loaded before linking against the API:

```java
if (FabricLoader.getInstance().isModLoaded("dropperutils")) {
    // Register the integration from your client initializer.
}
```

The current mod id remains `dropperutils`; the repository and mod id have not yet been renamed.

## Registering a mod

```java
import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.api.setting.SettingDefinition;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class ExampleClient implements ClientModInitializer {
    private boolean enabled = true;
    private int renderDistance = 12;

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("dropperutils")) {
            return;
        }

        ModDescriptor descriptor = ModDescriptor.builder("examplemod", "Example Mod")
            .description("Example integration settings")
            .version("1.0.0")
            .setting(SettingDefinition.bool(
                "enabled", "Enabled", "Enable the feature",
                true, () -> enabled, value -> enabled = value
            ))
            .setting(SettingDefinition.integer(
                "render_distance", "Render Distance", "Maximum distance",
                12, 2, 32, () -> renderDistance, value -> renderDistance = value
            ))
            .build();

        VantaClientApi.registerMod(descriptor);
    }
}
```

The example fields are illustrative. A real integration should bind getters and setters to its own configuration model. The setter is responsible for persisting the changed value until Vanta Client's shared persistence contract is introduced.

## Supported setting definitions

- Boolean
- Bounded integer
- Bounded decimal
- Bounded string
- Enum

Each setting has a stable ID, display name, description, default value, and typed getter/setter. Numeric definitions reject values outside their declared range. Enum definitions expose their constant names as options.

## Registration rules

- Mod and setting IDs must match `[a-z0-9][a-z0-9_.-]{0,63}`.
- A mod ID can be registered only once. Duplicate registration throws `IllegalArgumentException`; it never silently replaces another integration.
- Setting IDs must be unique within their owning mod.
- Descriptors and their setting lists are immutable after construction.
- Registry reads return an immutable snapshot sorted by mod ID.
- Registration is safe from concurrent initializers.
- The API has no Minecraft or Fabric types in its public method signatures.

## Compatibility policy

- Read `VantaClientApi.API_VERSION` to identify the API major version.
- Additive methods and setting types may be introduced within API version 1.
- Existing public method signatures and current setting semantics will not be changed incompatibly within API version 1.
- A future incompatible API will use a new major version and a migration period.

## In-game Vanta Client panel

The unified panel is opened from the Click GUI's **Settings** entry or through Mod Menu when Mod Menu is installed.

- **Settings** contains Vanta Client appearance preferences: accent colour, interface animations, compact layout, description visibility, and UI scale/spacing. Preferences are stored separately in `config/vanta-client.json`.
- **Mods** contains the built-in HUD, Visual, Combat, and Misc categories.
- The installed-mod sidebar is populated from Fabric Loader's loaded mod containers, using each mod's declared metadata. It does not scan or load arbitrary JAR files itself.
- Mods that register a `ModDescriptor` through Vanta Client API v1 expose their typed settings directly in the panel.
- Other installed mods are still listed. When Mod Menu is present, the panel can hand off to Mod Menu's own mod screen for configuration screens those mods provide.

## Optional Mod Menu integration

Mod Menu is an optional compile-time API dependency and runtime suggestion, not a required dependency. Vanta Client implements `ModMenuApi` to expose its own settings screen. The panel uses Mod Menu's public helper to open its standard mods screen; it does not reflect into Mod Menu internals or attempt to call private config factories belonging to other mods.

## Experimental best-effort hot-load attempt

The Mods landing page includes **Try hot-load new mod JARs**. It scans the instance's `mods` directory for JARs that were not loaded by Fabric and attempts to invoke their plain string-based `entrypoints.client` classes as `ClientModInitializer` instances.

This is an opt-in experiment, not a second Fabric Loader. It does not register discovered JARs as Fabric mod containers and cannot provide normal Fabric initialization semantics. It skips metadata that declares Mixins, access wideners, language adapters, unsupported environments, or dependencies that are not already loaded. Entrypoints must be public classes with public no-argument constructors and implement Fabric's `ClientModInitializer`.

Important limitations:

- It cannot apply Mixins or access wideners after startup.
- It does not resolve or load missing dependencies, process nested jars, or support custom entrypoint adapters.
- It does not register the JAR with Fabric Loader, so `FabricLoader.isModLoaded(id)` remains false for it and Fabric metadata/resource APIs will not treat it as a normal loaded mod.
- An initializer can still mutate global state, start threads, or register callbacks that Vanta cannot undo. A failed attempt may therefore leave partial side effects; restart Minecraft to return to a clean state.
- A successful invocation only means the declared client initializer returned without throwing. It is not proof that the mod is fully functional.

Use this only with trusted mods and expect many ordinary Fabric mods to be skipped or fail. Standard installation before launching Minecraft remains the supported path.

## Current scope

The panel supports boolean, bounded integer, bounded decimal, bounded string, and enum controls for Vanta API integrations. Integration setters remain responsible for persisting their own values. Shared persistence for third-party integrations, custom control renderers, and public release artifacts are not implemented yet.
