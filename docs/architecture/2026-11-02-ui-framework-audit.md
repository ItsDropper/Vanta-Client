# Vanta Client UI Framework — Repository Audit and Ecosystem Research

**Audit date:** 2026-11-02  
**Repository:** [ItsDropper/Vanta-Client](https://github.com/ItsDropper/Vanta-Client)  
**Branch / audited commit:** \`main\` / \`c1d2569a71c3f78f01a8e13a456865f8d4a08789\`  
**Scope:** client-side UI, input, settings, appearance, HUD rendering/editor, build configuration and UI-related dependencies.

This is a source audit, not a runtime or screenshot sign-off. No code was changed to produce this audit.

## 1. Build and runtime baseline

| Item | Repository value |
|---|---|
| Minecraft | \`1.21.11\` |
| Loader | Fabric Loader \`0.19.3\` |
| Fabric API | \`0.141.4+1.21.11\` |
| Java source / target | Java 21 |
| Build | Gradle + Fabric Loom \`1.17-SNAPSHOT\` |
| Mappings | Official Mojang mappings |
| Main language | Java |
| UI framework dependencies | None beyond Minecraft/Fabric APIs and Minecraft-provided UI/rendering classes |

The client source is under \`src/client/java\`. The mod declares a client entry point at \`name.vantaclient.client.DropperUtilsClient\`. The existing CI workflow runs \`./gradlew build\` on pushes and pull requests with JDK 21.

## 2. Actual screen entry points

The live GUI is not the old \`ClickGuiScreen\`.

- \`client/init/Events.java\` opens \`new VantaIntegrationsScreen(null)\` when the registered GUI key is consumed.
- \`ClickGuiScreen.init()\` redirects to \`VantaIntegrationsScreen\`. The old screen and its \`SettingsPanel\` / \`Setting\` component family are legacy code paths, not the main user-facing entry point.
- \`VantaIntegrationsScreen\` is the active settings and mod-library surface. It owns navigation, built-in feature settings, installed-mod discovery, Modrinth entry, appearance preferences, experimental mod hot-loading status and the Studio controls.
- \`VantaModrinthBrowserScreen\` is a separate screen with asynchronous search/install work and its own project-card construction.
- \`HudEditorScreen\` is a separate editor with custom element selection, dragging, grid, toolbar and settings-panel rendering.

This distinction matters: replacing \`ClickGuiScreen\` would not redesign the active GUI.

## 3. Current rendering and composition

The active screen uses the native Minecraft \`Screen\` lifecycle and \`GuiGraphics\` rendering path. Controls are commonly registered as Minecraft renderable widgets, particularly \`VantaPanelButton\`, \`VantaToggleSwitch\`, \`FeatureSliderWidget\` and \`EditBox\`. The surrounding composition is drawn manually from \`VantaIntegrationsScreen.render\` and helper methods.

The main screen is a large, stateful class (1,167 lines at the audited revision) with:

- Explicit section and selection enums.
- Fixed baseline coordinates, including a 920 × 540 logical panel.
- Repeated manual positioning for category, feature, setting and mod-library contents.
- A custom scale/offset conversion in \`VantaPanelButton\`.
- Hand-managed scroll indexes for some content.
- Per-mod hover animation state stored in a map.
- Separate drawing code for the shell, cards, labels, icons and controls.

This is workable for a small screen, but it does not provide a compositional layout contract. A new screen or control must currently know too much about pixel positions, color values and the exact host screen.

## 4. Controls, state, themes and persistence

- \`VantaPanelButton\` extends \`AbstractWidget\`, handles pointer clicks and narration, and has its own hover interpolation and rounded-rectangle renderer.
- \`VantaToggleSwitch\` also extends \`AbstractWidget\`, but repeats its own transition, hover, rounded drawing and color interpolation.
- \`FeatureSliderWidget\` is another custom widget family.
- The active built-in feature settings are backed by \`Feature\`, \`FeatureRegistry\` and \`SettingRegistry\`. Existing settings use suppliers and consumers to connect UI controls to real feature/config state.
- The public integration API exposes typed \`SettingDefinition\` values with validation, getter and setter bindings. This is a useful compatibility boundary and must remain intact.
- \`VantaClientPreferences\` persists appearance preferences including accent, animation, compact layout, descriptions, UI scale, panel opacity, HUD grid/snap and favorites.
- Appearance preferences are centralized as data, but component rendering still contains independent hardcoded surface/state colors and duplicated color-mixing logic. Having one accent preference is not the same as having a complete design-token system.

The old \`SettingsPanel\` path has its own width, animation, scrolling, widget positioning and drawing implementation. It should not be used as the template for the new framework.

## 5. HUD subsystem

The HUD runtime and editor have a different interaction model from settings screens.

- \`HudRenderer\` delegates to \`HudManager\`.
- \`HudManager\` owns a mutable registry of \`HudElement\` objects.
- \`HudElement\` stores normalized screen position and scale, computes its bounds in GUI-scaled coordinates and renders through \`GuiGraphics\`.
- \`HudEditorScreen\` draws the grid, hitboxes, selection outline, labels and toolbar separately from the main GUI.
- \`HudMouseHandler\` and \`HudSettingsPanel\` keep editor interaction state outside a shared component system.

The HUD's normalized positioning and element-specific rendering are valid domain concepts. The new framework should share tokens, drawing primitives, metrics and animation timing with the HUD editor, but it should not turn in-game HUD elements into ordinary screen widgets.

## 6. OneClient, OneConfig and PolyCompose research

### What OneClient actually is

OneClient is presented as a client/modpack ecosystem rather than a standalone in-game UI library. Its official website links the standalone modpack and a Rust launcher repository. The launcher repository describes itself as a monorepo for OneLauncher, OneClient and their core backend. The current Modrinth modpack's source link leads to Polyfrost's DataStorageV2 repository, whose README identifies it as static project/modpack data storage. Those are useful distribution/launcher sources, but the in-game configuration renderer is in OneConfig.

OneClient's own site says its unified configuration experience is built on OneConfig. Its claims about designer involvement and UI polish are product claims, not proof that any one framework automatically produces good visual design.

### OneConfig architecture

Inspected source: [Polyfrost/OneConfig, branch v1](https://github.com/Polyfrost/OneConfig/tree/v1).

The current OneConfig source is modular. Relevant modules include \`ui\`, \`poly-compose\`, \`internal\`, and Minecraft-version-specific integration code.

The inspected implementation shows:

- \`PolyCompose.install()\` registers Polyfrost implementations with Compose's platform graphics and text registries.
- \`PolyComposeHost\` drives Compose recomposition using a frame clock and a Recomposer. It has separate clocks for HUDs, previews and notifications.
- \`PolyComposeRuntime\` owns a root node, Compose \`Composition\`, custom applier and layout engine. It tracks changes and caches layout against content changes, viewport dimensions and font revisions.
- The PolyCompose node tree owns layout style, parent/child relationships and rendering/clipping.
- \`PolyCanvas\` maps Compose drawing operations to Polyfrost's own rendering context.
- \`ComposeScreen\` extends Minecraft \`Screen\`. It creates and disposes Compose scenes/recomposers; synchronizes dimensions and scale; forwards pointer movement, mouse buttons, wheel and keyboard events; adapts clipboard access; and handles lost focus, held-button release, scene failures and scene rebuilding.
- The Minecraft rendering integration has substantial custom code: \`BlazeRenderer\` batches and caches geometry/text and handles clipping, text baking and render-pipeline details; \`GlHost\` manages GPU render targets and composition paths, with resolution/texture-size guards.

This is the important lesson from PolyCompose: Compose did not eliminate the Minecraft integration problem. Polyfrost wrote a custom backend, scene host, input bridge, render adapter, font handling and lifecycle recovery around it.

### Reusability and OneConfig's public UI API

The OneConfig \`ui\` module exposes Compose-based APIs and theme access. Its \`OneConfigUI\` entry point is a service-loaded bridge for opening or creating OneConfig's own screen. The inspected \`OptionWidgets\` API currently provides a refresh operation for a OneConfig property. These are not a general-purpose Java API for hosting an arbitrary Vanta-owned screen without adopting the OneConfig UI stack.

The older OneConfig documentation explicitly discourages use of its old \`cc.polyfrost.oneconfig.elements\` system: it describes that system as proprietary and pending a rewrite because of portability and readability problems. That old element API is not a sound foundation for Vanta.

### Minecraft version and runtime dependencies

The current OneConfig README lists Fabric support from Minecraft 1.21.1 through 26.3, which includes Vanta's 1.21.11 target. It also states that OneConfig requires Compose Multiplatform and Fabric Language Kotlin, in addition to Fabric API on Fabric. The separately distributed Compose Multiplatform library is only a dependency bundle; its Modrinth description explicitly says it does not itself plug Compose into Minecraft.

Compatibility with the Minecraft version is therefore not the only question. Vanta would inherit a Kotlin/Compose runtime stack and still need a supported way to host and render Vanta-owned UI.

### Licensing

- OneConfig source is LGPLv3 **and** subject to Polyfrost's Additional Terms. The inspected license includes restrictions on use of Polyfrost/OneConfig trademarks and screen-visible attribution requirements for adaptations that use the library, subject to the license's stated exceptions. This is materially different from ordinary LGPL-only reuse and needs deliberate legal/product review before any adoption.
- JetBrains Compose Multiplatform's repository declares Apache-2.0. Third-party libraries included with it may have separate licenses.
- This ADR does not copy OneConfig or PolyCompose source. It uses only architectural observations.

## 7. Principal weaknesses in Vanta's current foundation

1. **No layout contract:** major screens rely on manually selected coordinates and repeated arithmetic rather than reusable measure/arrange containers.
2. **No shared component contract:** controls draw and animate independently; the visual behavior of buttons and toggles is duplicated.
3. **Incomplete tokenization:** accent selection exists, but surfaces, borders, text hierarchy, hover/focus/disabled states and motion are not fully centralized.
4. **Screen coupling:** the active screen owns too much layout, navigation, data discovery, texture lifecycle and content state.
5. **Inconsistent scrolling/clipping:** some lists use index-based paging and manual visibility; there is no common scroll viewport contract.
6. **Split interaction state:** screen widgets, custom mouse handling and HUD editor state use separate mechanisms.
7. **Independent HUD styling:** the HUD editor has custom renderer routines, colors and timing instead of shared UI primitives.
8. **No verified visual baseline in this audit:** this repository read was not a live Minecraft session and no runtime screenshots were captured.

## 8. Audit boundary and evidence

The audit is based on the GitHub source at the recorded commit and the linked upstream source/documentation. It is not a runtime performance measurement. The existing GitHub Actions workflow is configured to build on pushes, but no build result is claimed here.

Relevant upstream sources:

- [OneClient project](https://polyfrost.org/projects/oneclient)
- [OneClient Modrinth distribution](https://modrinth.com/project/oneclient-modpack)
- [OneLauncher / OneClient source monorepo](https://github.com/Polyfrost/OneLauncher)
- [OneClient distribution data](https://github.com/Polyfrost/DataStorageV2)
- [OneConfig source](https://github.com/Polyfrost/OneConfig/tree/v1)
- [OneConfig UI documentation](https://github.com/Polyfrost/OneConfig-Documentation)
- [JetBrains Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)
- [Compose Multiplatform compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
