# Vanta Client

Vanta Client is a Fabric client mod for Minecraft **1.21.11**. It combines configurable HUD overlays, visual and gameplay utilities, a custom settings interface, a Modrinth browser, and a public addon API.

## Feature overview

### HUD and on-screen information

- FPS, ping, CPS, keystrokes, armor, totem counter, potion effects, debug and performance overlays.
- A drag-and-drop HUD editor with saved positions, per-element scaling, grid alignment and snap-to-grid.
- Optional per-HUD hitbox backgrounds for custom and vanilla HUD elements. Enable **Show dark background** in the selected element's inspector to draw its actual bounds using dark gray at 80% opacity.
- **Connect nearby backgrounds** merges overlapping panels and can bridge adjacent hitboxes; set the connection distance from 0–24 GUI pixels. Overlaps are always drawn once, so opacity does not compound.
- VanillaHUD positioning for the hotbar, crosshair, health, armor, hunger, air, status effects, boss bar, mount health, scoreboard, experience bar and vehicle jump bar.
- Native vanilla rendering is retained for movable vanilla HUD components.

### Visual and quality-of-life features

- Fullbright, zoom, motion blur, nametag utilities and visual overlays.
- FarView distant-terrain LOD for singleplayer worlds.
- Local chat timestamps for signed player chat and system messages, with 24-hour, 24-hour-with-seconds and 12-hour formats; plus configurable animation profiles.
- Sprint/sneak toggles and double-tap sprint controls.
- Performance diagnostics and opt-in local CrashPatch diagnostics.
- Optional adaptive entity draw distance in Performance Optimizer: reduces client-side entity rendering distance under sustained frame load, then gradually restores the user's configured value when headroom returns. It never changes entity AI, ticks, collision, or server simulation.

### Combat and gameplay utilities

- Respawn-anchor optimization and explosion prediction.
- Low-fire and explosion-effect controls.
- Saturation display and other client-side HUD utilities.

Features can be enabled independently and retain their existing configuration. Optional per-feature toggle shortcuts can be assigned in Vanta Settings → Controls; they are unbound by default and saved with Minecraft's normal key mappings. The available settings depend on the selected feature.

## FarView limitations

FarView currently renders coarse **base terrain** generated from the integrated singleplayer server. It does not currently represent distant trees, structures, caves, or post-generation block edits. It does not extend multiplayer simulation distance or request full chunks across the configured far range. Its adaptive distance and quality settings are performance controls, not a guarantee of a particular frame rate.

## Mod library and hot-loading

The in-game mod library discovers installed Fabric mods and can open compatible Mod Menu configuration screens or Vanta addon settings. The Modrinth browser searches and installs compatible Fabric projects with required dependencies.

The optional **Enable now** path is experimental. It is not a replacement for Fabric Loader: it cannot apply Mixins or access wideners and cannot guarantee rollback after a failed entrypoint. Restart Minecraft after changing a mod JAR or when a session-load attempt fails.

## Vanta Client integration API

Third-party Fabric mods can register typed settings through the public API. See [docs/API.md](docs/API.md) for the API v1 contract and integration example.

Mod Menu is optional. When installed, Vanta exposes its settings through Mod Menu's public API and can hand off to compatible mods' own configuration screens.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Install Fabric API.
3. Place the Vanta Client JAR in the instance's `.minecraft/mods` directory.
4. Launch the game and open Vanta Client using its configured GUI key.

## Requirements

- Minecraft 1.21.11
- Fabric Loader
- Fabric API
- Java 21 runtime (provided by a compatible Minecraft launcher)

Mod Menu is optional.

## Development

The project uses Java 21, Fabric Loom and official Mojang mappings. Build with:

```sh
./gradlew build
```

The UI screenshot workflow launches the real Minecraft client through Fabric Client GameTests. See [docs/ui/SCREENSHOT_AUTOMATION.md](docs/ui/SCREENSHOT_AUTOMATION.md).

## License

See the repository license for details.
