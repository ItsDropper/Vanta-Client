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
- Performance Optimizer is enabled by default. It reuses tick-refreshed HUD snapshots and automatically adapts client-side entity draw distance under sustained frame pressure, then gradually restores the user's configured value when headroom returns. Both the optimizer and **Adaptive entity distance** can be disabled independently in Vanta Settings. The controller never changes entity AI, ticks, collision, or server simulation.
- FarView's **Adaptive Worker Pacing** is enabled by default independently of adaptive detail distance. Its background mesh worker yields when measured client frame/tick load rises, protecting foreground rendering while allowing distant terrain generation to catch up when headroom returns. This can be disabled in FarView settings.
- Performance diagnostics and opt-in local CrashPatch diagnostics.

### Combat and gameplay utilities

- Respawn-anchor optimization and explosion prediction.
- Low-fire and explosion-effect controls.
- Saturation display and other client-side HUD utilities.

Features can be enabled independently and retain their existing configuration. Optional per-feature toggle shortcuts can be assigned in Vanta Settings → Controls; they are unbound by default and saved with Minecraft's normal key mappings. The available settings depend on the selected feature.

## FarView limitations

FarView uses an adaptive quadtree to render distant terrain in singleplayer. It ingests immutable snapshots from real client chunks, including world-surface height, top-block map colour, and palette-compressed 3D block-state sections with captured lighting and block-model face colours. Captured sections are reduced into sparse 3D octrees and greedy voxel meshes with local ambient-occlusion shading. The octree node hierarchy (material, packed light, occupancy size, solid count and eight child references) is serialized alongside each section in cache format v7, so existing worlds reuse their built hierarchy after restart instead of rebuilding it for every mesh pass; legacy v1–v6 snapshots remain readable and lazily rebuild the missing hierarchy.  surface samples are also folded into the coarse LOD heightfield. Procedural heightfield shading includes directional ridge self-shadowing, and the **Terrain Shading** setting now genuinely disables both diffuse shading and those shadows. Snapshots persist in a versioned, compressed cache under each world's `vanta/farview/<dimension>` directory, with bounded background I/O. Captured terrain therefore survives game restarts and chunk unloads, including caves and vertical structure geometry while those chunks remain represented in the bounded cache. Uncaptured areas use procedural base-height sampling and a weighted 3×3 neighborhood of the real biome source for grass, surface-material and water colours, reducing hard colour boundaries between adjacent biomes. This is not full chunk generation: unexplored structures, surface decoration, caves, underground geometry, block entities and terrain edits are not guaranteed to appear. Unexplored terrain remains a surface heightfield rather than a full voxel LOD, and distant tree silhouettes are impostors rather than detailed foliage. It does not extend multiplayer chunk delivery or generate full chunks across the configured far range. Sampling density, terrain shading, distant water, tree silhouettes, and mesh-cache memory are independently configurable; higher detail and cache budgets consume more CPU and memory.

### Nvidium compatibility

Nvidium is optional and is not a compile-time, runtime, or bundled dependency. When it is installed and its accelerated renderer is active, Nvidium continues to own GPU-driven rendering and occlusion for Sodium's ordinary chunk terrain. FarView's distant LOD geometry remains in its separate Minecraft render pass, so the two render paths can coexist without FarView reaching into Sodium or Nvidium internals. The compatibility check uses reflection only and fails closed if Nvidium changes its internal status field.

Vanta's adaptive entity-distance controller and FarView background worker pacing do not modify Sodium or Nvidium terrain submission, visibility, mesh buffers, or occlusion state. This is renderer coexistence, not injection of FarView LOD meshes into Nvidium's mesh-shader pipeline: Nvidium's exposed API currently provides section visibility and region transforms, not a public custom-geometry upload/draw contract. FarView does not claim Nvidium GPU occlusion for its own LOD tiles.

On NVIDIA drivers exposing `GL_NV_mesh_shader`, FarView can use its own optional OpenGL mesh-shader backend without loading or linking against Nvidium. It uploads bounded terrain height/color grids to shader-storage buffers, dispatches 8×8-cell meshlets, and performs per-meshlet frustum and near/far-band rejection in the mesh shader. The existing Minecraft `VertexConsumer` path remains the fallback when the extension or shader program is unavailable. The initial GPU path deliberately falls back for terrain sets containing distant water cells; it also retains CPU-side tile planning, mesh generation, tree silhouettes, and edge skirts. This is GPU meshlet rendering and culling, not yet hierarchical GPU quadtree traversal or depth-pyramid occlusion.

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
