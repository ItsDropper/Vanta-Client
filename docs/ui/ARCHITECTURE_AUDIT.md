# Vanta Client UI Architecture Audit

**Audited branch:** `main`  
**Repository:** `ItsDropper/Vanta-Client`  
**Audit basis:** source inspection through GitHub at the time of this commit. No local build or in-game runtime inspection was performed.

## 1. Verified build environment

| Item | Repository value |
|---|---|
| Minecraft | `1.21.11` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.141.4+1.21.11` |
| Fabric Loom | `1.17-SNAPSHOT` |
| Java source/target | Java 21 (`options.release = 21`) |
| Mod version | `2.3.0` |
| Language | Java |
| UI/runtime dependencies | Minecraft GUI APIs; no Compose, Kotlin or external UI renderer declared |

The project uses official Mojang mappings and split main/client source sets. The UI is client-only under `src/client/java`.

## 2. Current rendering and screen architecture

### Active settings interface

`VantaIntegrationsScreen` is the active unified settings/mod browser. It extends Minecraft `Screen`, renders through `GuiGraphics`, and registers Minecraft `AbstractWidget` controls. It currently combines:

- Settings and Mods sections.
- Built-in feature categories and feature controls.
- Feature-specific setting controls.
- Installed-mod discovery and integration settings.
- Mod Menu hand-off and Modrinth browser navigation.
- Appearance preferences and custom panel drawing.

The screen uses a nominal 920×540 logical design. `VantaPanelButton` converts logical coordinates and dimensions to GUI-scaled screen coordinates using a fit scale and the saved `uiScale` preference. The screen also applies a pose translation and scale while drawing its custom background/content. This is a screen-specific coordinate convention, not a shared layout engine.

### Legacy ClickGUI

`ClickGuiScreen` also extends `Screen`, but its `init()` immediately switches to `VantaIntegrationsScreen`. The class still contains legacy feature list, settings-panel and hover-description rendering. This is dead/duplicated UI ownership and is a migration hazard; it should not be removed until all callers and compatibility paths are verified.

### Existing controls

- `VantaPanelButton`: custom rounded drawing, hover interpolation, selected state, action dispatch and accent supplier.
- `VantaToggleSwitch`: custom switch drawing, transition interpolation and value callbacks.
- `FeatureButton`: separate legacy control with its own toggle animation and mouse handling.
- `FeatureSliderWidget` / `FeatureSlider`: slider controls.
- Native `EditBox` and `AbstractWidget` are used for text entry and event registration.

These controls independently implement drawing and interaction conventions. There is no shared retained component tree, layout/measurement pass, focus model, clipping tree, or central animation clock.

### Settings model

`SettingRegistry` builds setting objects per feature. The current `Setting` interface includes presentation methods such as `addWidgets`, `updateWidgets`, `render`, and `clearWidgets`, coupling setting definitions to the legacy ClickGUI. The active integration screen separately pattern-matches setting subclasses to build controls. This duplicates presentation logic and limits reuse.

The public addon API's `SettingDefinition` is a better separation point for future adapters, but the built-in feature settings still need a migration-safe model boundary.

### HUD architecture

`HudRenderer` delegates to `HudManager`. The manager owns a static mutable element list and renders each `HudElement`. `HudEditorScreen` independently draws its grid, element hitboxes, selection outline, toolbar and settings panel. `HudMouseHandler` owns static hover/drag state and saves element positions on release.

HUD runtime rendering and HUD editor presentation are separate from the ClickGUI component code. The editor's input state is global/static rather than scoped to a screen session.

## 3. Appearance and scaling

`VantaClientPreferences` persists:

- Four accent presets (blue, violet, emerald, cyan).
- Interface animations, compact mode and setting descriptions.
- HUD grid visibility, grid size and snapping.
- UI scale and panel opacity.
- Favorite mod IDs.

The accent is available through a preference getter, and newer panel controls use an accent supplier. However, the legacy `FeatureButton` hardcodes its enabled color to `0xFF5577FF`, and `SettingsPanel` uses `0xFF6677FF`. The system is therefore not yet centrally themed.

Scaling helpers are embedded in `VantaPanelButton`. Some drawing is performed under a transformed pose stack while widgets are created with already-scaled coordinates. This split needs a single coordinate contract in the new runtime to avoid double-scaling and input mismatch.

## 4. Main architectural limitations

1. **No shared layout engine.** Most screen layout is absolute coordinates and manually computed offsets.
2. **Rendering and interaction are co-located.** Components own custom rendering and pointer handling with different conventions.
3. **No reusable component tree.** Rows, columns, clipping, overlays and scroll containers are manually assembled.
4. **Settings are presentation-coupled.** Built-in setting definitions know about ClickGUI widgets.
5. **Theme leakage.** Multiple hardcoded accent colors bypass Appearance preferences.
6. **Animation duplication.** Components advance their own interpolation in render calls, making motion timing dependent on render frequency and causing duplicated logic.
7. **HUD editor state is global.** Drag/hover state is not scoped to an editor lifecycle.
8. **Coordinate handling is fragmented.** Logical layout, Minecraft GUI-scaled coordinates and transformed drawing need a unified contract.
9. **Visual QA is not established.** Source inspection cannot prove visual quality, correct clipping, mouse mapping or runtime performance.

## 5. Existing functionality that must be preserved

- Feature enable/disable and config persistence.
- Feature-specific settings and their existing getters/setters.
- Addon API settings and installed-mod discovery.
- Mod Menu hand-off.
- Modrinth browser navigation.
- Appearance preferences and saved favorites.
- HUD element rendering, dragging, selection and saved positions.
- Keyboard navigation, text entry, screen close/parent navigation and Minecraft narration where already supported.

## 6. Audit boundaries

This audit is based on the source files inspected, not a complete runtime trace of every screen and input path. No Gradle build, Minecraft launch, screenshot capture, allocation profile or GUI-scale test was executed. Those remain explicit acceptance gates for the prototype.
