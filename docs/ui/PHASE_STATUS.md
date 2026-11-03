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

<badge>Core settings migrated; other screens remain separate</badge>

- `VantaSettingsScreen` is the primary client settings screen, opened by the GUI key and Mod Menu entry.
- Built-in HUD, Visual, Combat and Misc feature categories use the shared framework and retain their existing feature/settings getters, setters and config persistence.
- Appearance preferences are bound to the persisted preference model: accent, animations, compact layout, descriptions, UI scale and panel opacity.
- API-contributed mod settings use `VantaIntegrationSettingsScreen`, mapping BOOLEAN, INTEGER, DECIMAL, STRING and ENUM definitions to live framework controls.
- `VantaIntegrationsScreen` remains the separate installed-mod and integration browser with legacy/manual presentation.
- `VantaModrinthBrowserScreen` remains a separate screen with its own project cards and controls. Its network and installation work runs asynchronously.
- The HUD editor toolbar and selected-element inspector use the shared runtime and theme. The draggable HUD canvas, selection outlines and hitboxes remain specialized rendering by design.
- Every custom and vanilla HUD element has an independent, persisted background toggle. Backgrounds use a dark-gray fill at 80% opacity. Overlapping hitboxes are always unioned to prevent compounded alpha; the inspector can optionally bridge nearby hitboxes with a configurable 0–24 px connection distance.
- FPS, Ping, Keystrokes and CPS remain previewable/selectable in the HUD editor even while their in-game feature is disabled, so their positions and backgrounds can still be configured.

## Automated visual verification

<badge color="success">Latest screenshot workflow passed</badge>

The baseline commit `5beed53` passed both the Gradle build (run [41716076962](https://github.com/ItsDropper/Vanta-Client/actions/runs/41716076962)) and the original 36-image Minecraft screenshot workflow (run [41716077221](https://github.com/ItsDropper/Vanta-Client/actions/runs/41716077221)). Subsequent implementation batches also passed both workflows:

- `a25a09a`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41720774435) and [36-image screenshots](https://github.com/ItsDropper/Vanta-Client/actions/runs/41720774378).
- `6902953`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721290457) and [36-image screenshots](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721290497).
- `b23c391`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721788283) and [36-image screenshots plus scrollbar-drag assertion](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721788364).

The screenshot matrix covers the title screen, seven settings states and the HUD editor/inspector across four window-size / GUI-scale combinations. Before capturing, the client GameTest now drags a retained-framework scrollbar thumb and asserts that its content moves. Earlier visual review verified that the compact inspector sits below the toolbar at GUI scales 1 and 2. The automated workflow verifies screenshot capture and count; the complete latest image set has not been independently reviewed in this session.

## Remaining acceptance gates

- Review the latest screenshot artifact, particularly the title screen and HUD editor at 960×540 GUI scale 2.
- Manually exercise search, feature toggles, sliders, addon enum/string settings, dropdown keyboard navigation, screen open/close and HUD dragging in a real game session.
- Verify HUD hit-testing/drag behavior and FarView option restoration in gameplay.
- Confirm UI behavior with the user's preferred GUI scale and actual mod-library contents.
- The separate Modrinth browser and installed-mod library have not yet been fully migrated to the retained component tree.

A successful compile or screenshot capture is not by itself proof of full functional acceptance. Static screenshots do not replace manual interaction and gameplay testing.
