# Vanta UI Framework — Phase Status

**Updated:** 2026-11-04  
**Branch:** `main`

This status distinguishes implemented code, successful CI, screenshot inspection and manual gameplay validation.

## Foundation

<badge>Implemented; interaction coverage still expanding</badge>

- Retained `VantaUiNode` tree, `VantaUiRuntime` layout/input/focus/scroll handling and centralized `VantaUiTheme` tokens.
- Shared controls include buttons, toggles, sliders, dropdowns, text fields, indeterminate progress, text, tooltips and vector-style icons.
- IBM Plex Sans is bundled and wired through Minecraft's font-provider resources, with its OFL license.
- UI scale and panel opacity use shared framework behavior rather than separate transforms for each retained-mode screen.
- Dropdowns now use bounded floating overlays outside parent clipping, without layout reflow; keyboard navigation and popup-local wheel scrolling are supported.
- Remaining framework gaps include reusable modal dialogs and a comprehensive layout/input unit-test suite. Progress indicators are indeterminate because the Modrinth installer does not expose transfer callbacks. The Controls page edits the five registered Vanta mappings; no feature-level keybinding model currently exists.

## Production migration

<badge>Primary settings and mod-library routes use the shared runtime</badge>

- `VantaSettingsScreen` is the primary client settings screen, opened by the GUI key and Mod Menu entry.
- Built-in HUD, Visual, Combat and Misc feature categories use the shared framework and retain their existing feature/settings getters, setters and config persistence.
- Appearance preferences are bound to the persisted preference model: accent, animations, compact layout, descriptions, UI scale and panel opacity.
- API-contributed mod settings use `VantaIntegrationSettingsScreen`, mapping BOOLEAN, INTEGER, DECIMAL, STRING and ENUM definitions to live framework controls.
- `VantaIntegrationsScreen` now has a single retained-mode implementation for the installed-mod library and selected-mod details/config routing. The old manual renderer and duplicated widget path have been removed. The library retains favorites, search, refresh, Fabric/session load states, explicit per-mod hotload actions and mod-settings handoff. Local JAR inventory enumeration runs asynchronously; the Refresh action does not execute third-party entrypoints.
- `VantaModrinthBrowserScreen` also uses the retained component tree. Search, project icons, pagination, install and experimental session-load work retain their asynchronous handling; icon arrival does not rebuild the screen or steal search focus.
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
- `3b253f3`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41732524196) and [42-image real-client screenshots with dropdown overlay regression test](https://github.com/ItsDropper/Vanta-Client/actions/runs/41732524317).
- `fb63556`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41732997610) and [42-image screenshots with popup wheel/keyboard/parent-scroll tests](https://github.com/ItsDropper/Vanta-Client/actions/runs/41732997617).
- `be5f1eb`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41733534267) and [42-image screenshots](https://github.com/ItsDropper/Vanta-Client/actions/runs/41733534212).
- `f46f01c`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41735197828) and [46-image screenshots including the Controls page](https://github.com/ItsDropper/Vanta-Client/actions/runs/41735197888).
- `1b95d17`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41735937594) and [46-image screenshots with keyboard rebind/cancel/unbind lifecycle tests](https://github.com/ItsDropper/Vanta-Client/actions/runs/41735937566).
- `64fa3c9`: [build](https://github.com/ItsDropper/Vanta-Client/actions/runs/41736445767) and [46-image screenshots with duplicate-binding detection and key capture tests](https://github.com/ItsDropper/Vanta-Client/actions/runs/41736445960).

The 50-image suite covers the title screen, eight settings states including Controls, the HUD editor/inspector, desktop/compact Modrinth (including busy progress previews), installed-mod library, selected-mod details and the LAN world-sharing screen. The HUD editor capture enables FPS and Keystrokes backgrounds, places their hitboxes 2 px apart and asserts that they merge into one panel, then restores prior preferences. Client GameTests cover connected-region geometry, armor hitbox dimensions, scrollbar dragging, floating dropdown positioning/selection/scrolling, and Vanta keybind conflict/rebind/cancel/unbind behavior. The workflow verifies real Minecraft screenshot production and exact count; the images have not been independently reviewed for visual quality in this session.

## World sharing milestone

- The pause menu exposes a retained-mode Vanta world-sharing screen for singleplayer integrated servers. It uses Minecraft's existing LAN publishing API and lets the host select game mode and whether guests may use commands.
- This is LAN sharing, not Internet P2P. Remote invitations still require a dedicated authenticated signalling service and direct/relay transport; the current screen does not expose public addresses or claim Internet connectivity.
- The service has no-world GameTest coverage and the screen is included in the 50-image workflow. Publishing a real integrated server and joining from another device still require manual gameplay testing.
- The planned remote P2P design and its security/transport gates are documented in [REMOTE_WORLD_SHARING.md](../networking/REMOTE_WORLD_SHARING.md). No signalling endpoint, WebRTC dependency or relay service is shipped yet.

## Remaining acceptance gates
- Review the 50-image artifact for visual quality, particularly the Controls page, title screen, HUD editor at 960×540 GUI scale 2, compact Modrinth cards, busy progress previews, selected-mod details and world-sharing screen.
- Manually test mod search/favorites, read-only inventory refresh, per-mod hotload recovery, config handoff, dropdown scrolling, keybind conflict/capture/reset/unbind, screen lifecycle, and HUD dragging.
- Verify HUD hit-testing and FarView render-option restoration in a gameplay session; confirm layouts with the preferred GUI scale and a populated real mods folder.
A successful compile or screenshot capture is not by itself proof of full functional acceptance. Static screenshots do not replace manual interaction and gameplay testing.
