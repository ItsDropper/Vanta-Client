# Vanta UI Framework — Phase Status

**Updated:** 2026-11-03  
**Branch:** `main`

This status distinguishes implemented code, successful CI, screenshot inspection and manual gameplay validation.

## Foundation

<badge>Implemented; interaction coverage still expanding</badge>

- Retained `VantaUiNode` tree, `VantaUiRuntime` layout/input/focus/scroll handling and centralized `VantaUiTheme` tokens.
- Shared controls include buttons, toggles, sliders, dropdowns, text fields, text, tooltips and vector-style icons.
- IBM Plex Sans is bundled and wired through Minecraft's font-provider resources, with its OFL license.
- UI scale and panel opacity use shared framework behavior rather than separate transforms for each retained-mode screen.
- Remaining framework gaps include independent dropdown popups, a keybind editor, dialog/progress primitives and a comprehensive layout/input unit-test suite.

## Production migration

<badge>Primary settings and mod-library routes use the shared runtime</badge>

- `VantaSettingsScreen` is the primary client settings screen, opened by the GUI key and Mod Menu entry.
- Built-in HUD, Visual, Combat and Misc feature categories use the shared framework and retain their existing feature/settings getters, setters and config persistence.
- Appearance preferences are bound to the persisted preference model: accent, animations, compact layout, descriptions, UI scale and panel opacity.
- API-contributed mod settings use `VantaIntegrationSettingsScreen`, mapping BOOLEAN, INTEGER, DECIMAL, STRING and ENUM definitions to live framework controls.
- `VantaIntegrationsScreen` now uses the retained component tree for the installed-mod library and selected-mod details/config routing. The main library retains favorites, search, scan, Fabric/session load states, hotload actions and mod-settings handoff. Some older fallback rendering code remains in the class, but the primary route uses the shared runtime.
- `VantaModrinthBrowserScreen` also uses the retained component tree. Search, project icons, pagination, install and experimental session-load work retain their asynchronous handling; icon arrival does not rebuild the screen or steal search focus.
- API-contributed mod settings use `VantaIntegrationSettingsScreen`, which is also backed by the shared runtime.
- The HUD editor toolbar and selected-element inspector use the shared runtime and theme. The draggable HUD canvas, selection outlines and hitboxes remain specialized rendering by design.
- Every custom and vanilla HUD element has an independent, persisted background toggle. Backgrounds use a dark-gray fill at 80% opacity. Overlapping hitboxes are always unioned to prevent compounded alpha; the inspector can optionally bridge nearby hitboxes with a configurable 0–24 px connection distance.
- FPS, Ping, Keystrokes and CPS remain previewable/selectable in the HUD editor even while their in-game feature is disabled, so their positions and backgrounds can still be configured.
- HUD background, selection and mouse hit-testing use the same rendered hitbox geometry. The horizontal armor HUD includes its above-icon durability labels, and its vertical layout uses the actual four-slot height rather than an oversized placeholder bound.

## Automated visual verification

<badge color="success">Latest screenshot workflow passed</badge>

The baseline commit `5beed53` passed both the Gradle build (run [41716076962](https://github.com/ItsDropper/Vanta-Client/actions/runs/41716076962)) and the original 36-image Minecraft screenshot workflow (run [41716077221](https://github.com/ItsDropper/Vanta-Client/actions/runs/41716077221)). Subsequent implementation batches also passed both workflows:

- `a25a09a`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41720774435) and [36-image screenshots](https://github.com/ItsDropper/Vanta-Client/actions/runs/41720774378).
- `6902953`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721290457) and [36-image screenshots](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721290497).
- `b23c391`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721788283) and [36-image screenshots plus scrollbar-drag assertion](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721788364).
- `51ea19e`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41723780145) and [36-image screenshots with HUD-background grouping assertion](https://github.com/ItsDropper/Vanta-Client/actions/runs/41723780313).
- `312503f`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41729020014) and [40-image screenshots](https://github.com/ItsDropper/Vanta-Client/actions/runs/41729019964).
- `f58bf71`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41729498033) and [40-image screenshots with two connected HUD backgrounds](https://github.com/ItsDropper/Vanta-Client/actions/runs/41729498054).
- `5209a47`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41729746806) and [42-image screenshots including installed-mod details at desktop and compact sizes](https://github.com/ItsDropper/Vanta-Client/actions/runs/41729746874).
- `00bab4a`: [README/documentation build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41729939536).

The 42-image suite covers the title screen, seven settings states and the HUD editor/inspector across four window-size / GUI-scale combinations, plus desktop and compact captures of the Modrinth browser, installed-mod library and selected-mod details view. The HUD editor capture enables the FPS and Keystrokes backgrounds, places their hitboxes 2 px apart, and asserts that they become one continuous background. The test restores the previous positions, background toggles, connection distance and snap-to-grid preference afterward. Other client GameTests cover connected-region geometry, armor hitbox dimensions and retained-framework scrollbar dragging. The workflow verifies real Minecraft screenshot production and exact count; the latest images have not been independently reviewed for visual quality in this session.

## Remaining acceptance gates

- Review the latest screenshot artifact, particularly the title screen and HUD editor at 960×540 GUI scale 2.
- Manually exercise search, feature toggles, sliders, addon enum/string settings, dropdown keyboard navigation, screen open/close and HUD dragging in a real game session.
- Verify HUD hit-testing/drag behavior and FarView option restoration in gameplay.
- Confirm UI behavior with the user's preferred GUI scale and actual mod-library contents.
- Review the latest 42-image artifact, particularly HUD background grouping, the HUD editor at 960×540 GUI scale 2, compact Modrinth cards and selected-mod details.
- Exercise mod search, favorites, scan, details/config handoff, hotload error recovery, feature toggles, sliders, addon enum/string settings, dropdown keyboard navigation and screen open/close in a real game session.
- Verify HUD hit-testing/drag behavior and FarView option restoration in gameplay. CI does not replace this manual acceptance.

A successful compile or screenshot capture is not by itself proof of full functional acceptance. Static screenshots do not replace manual interaction and gameplay testing.
