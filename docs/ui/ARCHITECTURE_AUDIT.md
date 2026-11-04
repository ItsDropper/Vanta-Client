# Vanta Client UI Architecture Audit

**Audited branch:** \`main\`  
**Primary source audit:** [\`docs/architecture/2026-11-02-ui-framework-audit.md\`](../architecture/2026-11-02-ui-framework-audit.md)  
**Purpose of this document:** concise engineering baseline for the framework work.

## 1. Verified build baseline

| Item | Repository value |
|---|---|
| Minecraft | \`1.21.11\` |
| Fabric Loader | \`0.19.3\` |
| Fabric API | \`0.141.4+1.21.11\` |
| Fabric Loom | \`1.17-SNAPSHOT\` |
| Java source/target | Java 21 |
| Language | Java |
| Rendering baseline | Minecraft \`Screen\`, \`GuiGraphics\`, \`AbstractWidget\` |

The project uses official Mojang mappings and split main/client source sets. Client UI code is under \`src/client/java\`. The repository has a GitHub Actions build workflow that runs \`./gradlew build\` on pushes and pull requests with JDK 21.

## 2. Actual screen ownership

The GUI key opens `VantaIntegrationsScreen`, which owns the installed-mod library and selected-mod details. Its header links to `VantaSettingsScreen`, `VantaModrinthBrowserScreen` and `HudEditorScreen`. Built-in feature categories, feature settings and appearance preferences live in `VantaSettingsScreen`; third-party Mod Menu/config screens remain external handoffs.

`ClickGuiScreen.init()` redirects to the unified integrations screen and is a legacy compatibility path, not the main user-facing ClickGUI. The HUD editor remains a distinct interaction surface because its canvas selection and dragging model differs from ordinary settings widgets.
Other distinct screens include the Modrinth browser and \`HudEditorScreen\`. The HUD editor is a different interaction surface from the settings panel and should not be collapsed into ordinary settings widgets.

## 3. Current implementation

- `VantaSettingsScreen` uses `VantaUiNode`, `VantaUiRuntime` and `VantaUiTheme` for built-in HUD, Visual, Combat and Misc categories, feature settings and appearance preferences.
- `VantaIntegrationsScreen` uses the retained runtime for installed-mod inventory, favorites, details and configuration handoff. Its former manual-widget renderer and duplicated settings branches have been removed.
- `VantaModrinthBrowserScreen` also uses the retained runtime for discover/search, installed state, pagination and install actions; network work remains asynchronous.
- Local mod inventory refresh enumerates JAR metadata on a worker thread and does not invoke entrypoints. Experimental session loading is a separate, explicit per-mod action.
- The public addon API still exposes typed `SettingDefinition` values; `VantaIntegrationSettingsScreen` maps supported types into retained controls.
- Older `VantaPanelButton`, `VantaToggleSwitch` and `FeatureSliderWidget` implementations remain for legacy/compatibility surfaces, not as the rendering foundation for active settings and library routes.
- Legacy \`ClickGuiScreen\`, \`SettingsPanel\` and \`Setting\` implementations contain their own widget/render behavior and should not become the new framework template.
- \`HudRenderer\` delegates to \`HudManager\`; \`HudElement\` objects own in-game HUD positioning/rendering; \`HudEditorScreen\` and \`HudMouseHandler\` separately manage editor presentation and pointer state.

## 4. Appearance, scaling and state

\`VantaClientPreferences\` persists accent preset, animation preference, compact mode, descriptions, UI scale, panel opacity, HUD grid/snap, and favorite mods. This is a useful existing source of truth, but the old renderer has additional hardcoded surfaces and state colors. Accent selection alone is not a complete theme system.

The settings and library screens use `VantaUiRuntime` to map Minecraft GUI-scaled mouse coordinates into logical layout coordinates and apply one shared scale transform. The framework owns pointer hit-testing, focus, clipping, scrolling and floating dropdown overlays; screens should not layer a second widget-specific scaling conversion on top.

## 5. Highest-impact limitations

1. The retained framework still needs broader interaction tests beyond targeted smoke tests and screenshot coverage.
2. The Controls page edits the five global Vanta mappings and optional per-feature toggle shortcuts for every registered feature. All mappings share Minecraft/Vanta duplicate detection and support keyboard/mouse capture, reset and unbind. Generic dialog/progress components and comprehensive layout/input tests remain unfinished.
3. External mods that provide their own Mod Menu config screens remain outside Vanta's framework control.
4. HudElement width/height measurements are cached per manager layout revision, eliminating per-frame font measurement and dynamic width scans in hitbox/background calculations; scale is applied at read time and orientation-changing setters invalidate explicitly. The HUD canvas selection/drag overlay intentionally remains specialized rendering; only its toolbar and inspector use the shared component runtime. Custom HUD backgrounds reuse HudManager's tick-refreshed active-element snapshot instead of repeating visibility predicates every frame; vanilla HUD layout backgrounds use a dedicated registry with cached preference IDs, independent of custom HUD visibility.
5. Manual gameplay-session validation and performance profiling remain outstanding.

## 6. Performance controls

- Potion-effect HUD labels and durations are snapshotted once per five game ticks, shared by rendering and hitbox sizing, and invalidated on world/player changes or game-time rewind; the global Performance Optimizer toggle is not required for this basic render-path cache. Totem inventory counts, Armor HUD equipment/durability presentation, Ping HUD player-info labels and CPS deque counts are snapshotted once per game tick in normal mode, shared by rendering and hitbox sizing, and invalidated on relevant game-time and world/player/connection changes. CPS deque pruning/locking is therefore performed once per tick rather than in render and width queries.
- The Performance Optimizer's adaptive entity-distance controller uses the native 1.21.11 `Options.entityDistanceScaling()` setting rather than a renderer mixin. It is opt-in, captures and restores the user's configured value, detects external option changes, and waits for fresh world-render timing samples before adjusting.
- The controller changes only client-side entity draw range. Entity ticking, AI, collision and server simulation remain untouched.
- FarView's desired-tile quadtree is rebuilt only for initial planning, four-chunk player movement, or topology-affecting settings changes; idle ticks no longer allocate and sort a fresh tile list. Bounds and LOD threshold checks use squared distances. Runtime status exposes plan rebuilds, tile generation, submission timing, draw-budget use and processed tile counts. Mesh sample coordinates are precomputed once per tile and retained for rendering surfaces and skirts, avoiding repeated per-quad world-coordinate arithmetic and keeping vertex positions aligned with the sampling grid. The worker precomputes fully submerged cell flags into a compact bitset, so the per-frame render loop performs one bit lookup instead of four height comparisons per candidate quad. Vertex emission reuses a frame-local position vector and passes packed opaque ARGB colors directly to the existing shared render consumer, avoiding per-vertex temporary-vector allocations and color unpacking. Per-quad distance tests are skipped only when nearest/farthest tile bounds prove the entire tile is inside the visible annulus; tiles touching either boundary retain individual cell checks. Per-tile horizontal view-cone rejection uses a projection formula with precomputed angle constants instead of repeated `atan2`/`cos` calls, and biases borderline cases toward rendering. Desired-tile membership is an immutable snapshot replaced atomically during replans. A new plan prunes obsolete queued tile jobs, idle scheduling avoids rebuilding generator context when no work is ready, and active jobs abandon work between rows; completed meshes are revision-stamped and revalidated before cache insertion, preventing late worker results from crossing world transitions. Generation metrics are recorded only after client-thread acceptance. When adaptive mode is enabled, worker pacing backs off under measured frame/tick pressure instead of competing at full rate. Adaptive draw-budget changes are evaluated every 10 renders using smoothed submission cost, with smaller recovery increments and retained quality-profile ceilings.
- Entity occlusion, chunk mesh compilation and block-entity batching are intentionally not reimplemented here because dedicated renderer mods already own those pipelines; overlapping render hooks would increase compatibility risk.

## 7. Existing functionality to preserve

- Feature enable/disable and configuration persistence.
- Feature-specific settings and existing getters/setters.
- Public addon API and typed integration settings.
- Mod Menu hand-off and Modrinth navigation.
- Appearance preferences and favorites.
- HUD element rendering, normalized positioning, scaling, dragging, selection and persistence.
- Minecraft keyboard, mouse, screen lifecycle and narration behavior.

## 8. Verification boundary

This is a source audit. The existence of the push-triggered workflow does not mean that its build has passed. No local Minecraft session, screenshot capture, GUI-scale test, allocation profile or gameplay performance measurement is claimed here. Those require an actual build/run and visual QA pass.

For deeper source findings, including the active OneClient/OneConfig architecture, see:
- [Repository audit and ecosystem research](../architecture/2026-11-02-ui-framework-audit.md)
- [OneClient / Polyfrost technical research](ONECLIENT_RESEARCH.md)
- [Architecture decision record](ADR-001-ui-foundation.md)
