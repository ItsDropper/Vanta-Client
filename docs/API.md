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

## Current scope

This milestone establishes registration, typed setting definitions, validation, and registry access. The in-game integrations browser, shared config persistence, custom controls, and public release artifacts are not implemented yet. Integrations should not assume that registering a descriptor currently creates a visible GUI screen.
