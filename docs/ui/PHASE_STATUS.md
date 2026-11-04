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
- Remaining framework gaps include reusable modal dialogs and a comprehensive layout/input unit-test suite. Progress indicators are indeterminate because the Modrinth installer does not expose transfer callbacks. The Controls page now edits the five global Vanta mappings plus one optional, unbound-by-default shortcut for each registered feature; feature bindings use Minecraft's normal options persistence and participate in shared conflict detection.
- The framework, legacy native widgets, title-screen controls and HUD-editor chrome now share cached fourth-order superellipse geometry. Existing rounded helper names remain compatibility aliases; scanline insets are precomputed and no shader is required.
- Semantic notification colors are centralized in `VantaUiTheme`, and keyboard-focused buttons receive an explicit accent outline distinct from hover.
- HUD-editor element guides now use the exact persisted/rendered hitbox rectangle; selection emphasis remains a separate outer outline.
- HUD background hitbox collection and connected-region merging use reusable primitive scratch arrays on the render thread. The normal path no longer creates temporary lists, queues or rectangle records per frame; scratch storage grows only if a layout exceeds its initial capacity.
- The title screen keeps Minecraft's vanilla panorama and original button actions while routing its surface geometry through the shared theme.

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

The 50-image suite covers the title screen, eight settings states including Controls, the HUD editor/inspector, desktop/compact Modrinth (including busy progress previews), installed-mod library, selected-mod details and the LAN world-sharing screen. The HUD editor capture enables FPS and Keystrokes backgrounds, places their hitboxes 2 px apart and asserts that they merge into one panel, then restores prior preferences. Client GameTests cover connected-region geometry, armor hitbox dimensions, scrollbar dragging, floating dropdown positioning/selection/scrolling, and global/feature keybind conflict, rebind, cancel and unbind behavior. The workflow verifies real Minecraft screenshot production and exact count; the images have not been independently reviewed for visual quality in this session.

## Performance optimization

- Performance Optimizer still provides tick-rate HUD data snapshots and render/tick timing metrics. It now also offers an opt-in adaptive entity draw-distance controller using Minecraft 1.21.11's native entity-distance option.
- The controller measures fresh world-render samples, reduces only client entity draw distance in bounded steps under sustained load, and recovers gradually when frame headroom returns. It retains the user's selected value as its ceiling, adopts external option changes, and restores the saved value when disabled or leaving a world.
- Entity AI, entity ticking, collision, hitboxes, server simulation and networking are not modified. The minimum retained draw-distance ratio is configurable from 50–100% and defaults to 65%.
- GameTests cover overload response, floor/ceiling bounds, gradual recovery, neutral conditions and missing measurement behavior. Actual FPS gains remain scene- and hardware-dependent and require gameplay benchmarking.
- FarView now rebuilds its quadtree tile plan only on initial entry, four-chunk movement, or topology-affecting setting changes. Its quadtree bounds and LOD thresholds use squared distances to avoid repeated square roots. Status diagnostics expose plan rebuild cost/count, last/average mesh generation time, mesh submission time, draw budget consumption, and processed tile count. Mesh sample coordinates are precomputed once per tile, and quad distance checks are skipped only when conservative tile bounds prove every point is inside the visible annulus.

## World sharing milestone

- The retained-mode Vanta Share screen is available from the title screen and singleplayer pause menu. It supports a user-configured self-hosted relay URL, local integrated-server publication, remote invite creation, claimed guest-name display, host approval/rejection, guest join, cancellation and disconnect.
- The client session manager owns host/guest state outside the screen, keeps relay credentials in memory, forwards each approved stream through a dedicated tunnel, and stops tunnels on disconnect, world close or client exit.
- Guest Minecraft connects to a loopback-only TCP port using vanilla `ConnectScreen`; the host side forwards to the fixed loopback integrated-server port. Minecraft's ordinary login path remains intact.
- Hosting also publishes the integrated server to the host's local network. The UI warns about LAN discovery and identifies guest names as unverified claims.
- Approved guests negotiate a reliable, ordered WebRTC DataChannel using authenticated relay signalling. ICE may establish a direct peer path or select TURN; the authenticated WebSocket gameplay relay is the failure fallback. The relay still coordinates invites/approval and supplies ICE configuration.
- No Vanta-operated public relay/STUN/TURN endpoint is deployed or configured in the client. Real cross-network hosting still requires an operator-provided HTTPS relay and working STUN/TURN infrastructure, followed by testing with two independent Minecraft clients. Code-level WebRTC support is not proof that arbitrary NAT combinations work. See [REMOTE_WORLD_SHARING.md](../networking/REMOTE_WORLD_SHARING.md) for the implemented flow and acceptance gates.

## Remaining acceptance gates
- Review the 50-image artifact for visual quality, particularly the Controls page, title screen, HUD editor at 960×540 GUI scale 2, compact Modrinth cards, busy progress previews, selected-mod details and world-sharing screen.
- Manually test mod search/favorites, read-only inventory refresh, per-mod hotload recovery, config handoff, dropdown scrolling, global and per-feature keybind conflict/capture/reset/unbind, screen lifecycle, and HUD dragging.
- Verify HUD hit-testing and FarView render-option restoration in a gameplay session; confirm layouts with the preferred GUI scale and a populated real mods folder.
A successful compile or screenshot capture is not by itself proof of full functional acceptance. Static screenshots do not replace manual interaction and gameplay testing.
