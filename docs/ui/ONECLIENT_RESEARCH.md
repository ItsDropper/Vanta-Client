# OneClient / Polyfrost UI Research

**Research date:** 2026-11-02  
**OneConfig ref inspected:** \`Polyfrost/OneConfig:v1\`  
**Vanta target:** Minecraft 1.21.11 / Fabric / Java 21

## 1. Scope and repository distinction

OneClient is a Minecraft client/modpack ecosystem, not a standalone UI toolkit. Its site describes its unified configuration experience as being built on OneConfig. Polyfrost's Rust \`OneLauncher\` repository is a distribution/launcher monorepo; it should not be confused with the in-game UI implementation.

The in-game implementation inspected here is the current OneConfig \`v1\` source. It contains both historical UI module material and a newer Compose-based Minecraft UI path. The README for \`modules/ui\` still mentions a NanoVG/PolyUI UIManager bridge. That statement must not be used alone to characterize the active 1.21.11 UI path: the current Minecraft module also contains \`ComposeScreen\`, \`ComposeSupport\`, \`GlHost\`, \`BlazeRenderer\`, \`OneConfigUIScreen\` and a \`PolyCompose\` runtime.

Sources:
- https://polyfrost.org/projects/oneclient
- https://github.com/Polyfrost/OneLauncher
- https://github.com/Polyfrost/OneConfig/tree/v1
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/ui/README.md

## 2. PolyCompose is a custom Compose backend, not just Compose widgets

The \`modules/poly-compose\` source installs Polyfrost implementations into Compose's platform graphics and text registries (\`PolyCompose.install()\`). Its \`PolyCanvas\` maps Compose drawing operations to a Polyfrost renderer.

The runtime is more substantial than a convenience wrapper:

- \`PolyComposeHost\` creates separate \`PolyComposeClock\` instances for HUDs, previews and notifications.
- Each clock owns a \`BroadcastFrameClock\`, an unconfined coroutine scope and a Compose \`Recomposer\`. Its frame method sends snapshot notifications, advances the frame clock and reports whether recomposition changed.
- \`PolyComposeRuntime\` owns a \`RootNode\`, a custom \`PolyApplier\` and a Compose \`Composition\`. It sets content, advances frames, lays out the tree and disposes the composition.
- It tracks content/applier revisions, viewport width/height and font-manager revision. \`layoutIfChanged\` avoids repeating a full layout pass when those inputs are unchanged and supports content-only remeasurement.
- \`PolyNode\` owns child nodes, parent relationships, layout style, drawing, clipping and optional cached decorations. Its renderer honors clip-to-bounds, rectangular/rounded clips, backgrounds, borders and fading.
- \`PolyLayoutEngine\` has explicit measure and arrange passes for Row, Column and Box layouts, including fill, wrap, percent and pixel sizing, padding, gaps, margins and alignment.

This is a useful demonstration of why a declarative UI in Minecraft needs a real runtime, a component tree, layout invalidation, clipping and rendering integration. Compose itself does not provide those Minecraft-specific pieces.

Sources:
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/poly-compose/src/main/kotlin/org/polyfrost/compose/backend/PolyCompose.kt
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/poly-compose/src/main/kotlin/org/polyfrost/compose/backend/PolyCanvas.kt
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/poly-compose/src/main/kotlin/org/polyfrost/compose/runtime/PolyComposeHost.kt
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/poly-compose/src/main/kotlin/org/polyfrost/compose/runtime/PolyComposeRuntime.kt
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/poly-compose/src/main/kotlin/org/polyfrost/compose/node/PolyNode.kt
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/poly-compose/src/main/kotlin/org/polyfrost/compose/layout/PolyLayoutEngine.kt

## 3. Minecraft integration: ComposeScreen

The Minecraft adapter is implemented in \`minecraft/.../ComposeScreen.kt\`. For the 1.21.11 path it extends Minecraft \`Screen\` and creates a Compose \`CanvasLayersComposeScene\`, \`FrameRecomposer\`, rendering scope and platform scene context.

The adapter takes responsibility for:

- Creating, binding, closing and optionally retaining a Compose scene.
- Synchronizing window dimensions, viewport dimensions, density and surface scale.
- Advancing recomposition and measuring/layout before drawing.
- Rendering on demand or continuously depending on the screen's render mode and dirty state.
- Routing pointer movement, mouse button press/release, wheel/trackpad scrolling, key press/release and character events into Compose.
- Translating Minecraft key data to Compose/AWT events and providing clipboard bridges.
- Releasing held pointer buttons when the window loses focus or the screen is removed.
- Detecting failed scenes, discarding them, rebuilding and limiting repeated recovery attempts.
- Resetting pointer/cursor state during screen lifecycle transitions.

The renderer path is also version-specific. On Minecraft 1.21.11, \`ComposeScreen\` uses a recording/render-state path and \`GuiGraphicsExtractor\`, rather than simply drawing a Compose canvas directly into a normal \`Screen.render\` call.

Sources:
- https://github.com/Polyfrost/OneConfig/blob/v1/minecraft/src/main/kotlin/org/polyfrost/oneconfig/internal/ui/compose/ComposeScreen.kt
- https://github.com/Polyfrost/OneConfig/blob/v1/minecraft/src/main/kotlin/org/polyfrost/oneconfig/internal/ui/compose/ComposeSupport.kt

## 4. Renderer and GPU integration

\`BlazeRenderer\` is a custom adapter from Polyfrost's rendering/tessellation operations into Minecraft GUI render operations. The inspected source includes mesh operations, texture setup, glyph/text handling, bitmap texture references, layer composition and cached/captured drawing data. It keys cached state against GUI scale and screen dimensions and checks whether captured bitmap resources remain valid before replaying them.

\`GlHost\` owns additional screen/HUD GPU integration, render targets and texture wrappers. The 1.21.11 source selects a distinct pipeline from older OpenGL-only branches; the implementation includes compatibility branches for VulkanMod and version-specific Minecraft rendering APIs.

This is important: OneConfig's current result is not simply Compose drawing into an arbitrary framebuffer. Polyfrost maintains a substantial renderer/backend to preserve Minecraft's GUI composition, texture and lifecycle behavior.

Sources:
- https://github.com/Polyfrost/OneConfig/blob/v1/minecraft/src/main/kotlin/org/polyfrost/oneconfig/internal/ui/compose/BlazeRenderer.kt
- https://github.com/Polyfrost/OneConfig/blob/v1/minecraft/src/main/kotlin/org/polyfrost/oneconfig/internal/ui/compose/GlHost.kt

## 5. Public reusability and PolyCompose's limits

OneConfig's public \`OneConfigUI\` API is a service-loaded bridge. It can open OneConfig's own UI on a route, create a OneConfig screen or test whether a screen belongs to OneConfig. The actual implementation is provided by a platform bridge in the Minecraft module.

\`OptionWidgets.refresh(Property)\` exposes a narrow refresh operation for a OneConfig property. It does not provide a generic adapter for arbitrary third-party setting models.

The inspected public APIs therefore do not amount to a general Java interface for hosting arbitrary Vanta screens in OneConfig's Compose runtime. The \`poly-compose\` module is a meaningful implementation that another project could study or potentially integrate with substantial coupling, but adopting it does not remove the need for a Minecraft screen host, a compatible renderer and lifecycle/input integration.

Sources:
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/ui/src/main/kotlin/org/polyfrost/oneconfig/api/ui/v1/OneConfigUI.kt
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/ui/src/main/kotlin/org/polyfrost/oneconfig/api/ui/v1/OptionWidgets.kt
- https://github.com/Polyfrost/OneConfig/blob/v1/minecraft/src/main/kotlin/org/polyfrost/oneconfig/api/ui/v1/internal/OneConfigUIBridgeImpl.kt

## 6. Dependencies and runtime implications

The OneConfig Compose bundle nests a substantial set of Compose runtime, saveable, Foundation, Material, UI, navigation, lifecycle and viewmodel artifacts. Its Gradle build also excludes duplicate Kotlin/Compose artifacts and generates nested jars. The current version catalogue declares Kotlin, Kotlin coroutines, Fabric Language Kotlin, Compose and Skiko-related dependencies. The Compose bundle's Fabric metadata requires Fabric Language Kotlin.

This has real implications for Vanta:

- A Java-only Gradle project would need Kotlin plugin/compiler integration.
- The dependency graph becomes larger and more version-sensitive.
- Desktop Compose brings AWT/Skiko concerns into a Minecraft runtime.
- Platform-specific native runtime packaging and load behavior become part of the support burden.
- Vanta would still need to solve Minecraft input forwarding, GUI scale, render-thread rules, clipping, frame scheduling, resource disposal and version-specific GUI rendering.

The OneConfig \`ComposeSupport\` source itself probes AWT availability and disables the UI when the runtime lacks working AWT support. That is a concrete example of the runtime assumptions the integration would inherit.

Sources:
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/compose-bundle/build.gradle.kts
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/compose-bundle/src/main/resources/fabric.mod.json
- https://github.com/Polyfrost/OneConfig/blob/v1/gradle/libs.versions.toml
- https://github.com/Polyfrost/OneConfig/blob/v1/minecraft/src/main/kotlin/org/polyfrost/oneconfig/internal/ui/compose/ComposeSupport.kt

## 7. Licensing

The OneConfig repository declares LGPLv3 together with Polyfrost's Additional Terms. The existing repository audit records that the additional terms include project-specific trademark and visible attribution conditions for adaptations using the library. This is not equivalent to adopting a plain LGPL dependency; legal and user-facing product implications require explicit review.

The Compose bundle's Fabric metadata declares Apache-2.0, but that does not relicense OneConfig or all third-party dependencies. Compose and its bundled dependencies must be assessed under their respective licenses.

Vanta's prototype does not copy OneConfig/PolyCompose source or assets and introduces no third-party UI dependency.

Sources:
- https://github.com/Polyfrost/OneConfig/blob/v1/LICENSE
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/compose-bundle/src/main/resources/fabric.mod.json
- https://github.com/Polyfrost/OneConfig-Documentation

## 8. Transferable lessons for Vanta

The most valuable aspects to copy as engineering principles—not code or branding—are:

1. Keep a screen host separate from the reusable component/runtime layer.
2. Keep a retained component tree with explicit ownership and disposal.
3. Separate measurement/arrangement from rendering.
4. Use one consistent coordinate and density convention.
5. Route input through a single path; explicitly handle capture, release and lost focus.
6. Make clipping part of layout/hit-testing rather than a per-screen afterthought.
7. Invalidate layout based on content, viewport and text/font changes.
8. Use state-driven composition and a shared frame/motion policy.
9. Cache only where the cache has a reliable content key and resource-validity check.
10. Keep HUD domain behavior separate from general screen layout while sharing theme tokens and primitives.

None of these principles imply that Vanta needs to reproduce Compose's compiler/runtime model.

## 9. Vanta recommendation

For Vanta's current Java 21 / Fabric 1.21.11 project, the selected direction remains a small Vanta-native retained/declarative framework on Minecraft's GUI APIs.

The first implementation is an isolated prototype. It intentionally uses local demo state for controls that are not yet safely adapted to real game settings; the FPS readout is live, and the accent/motion preferences bind to Vanta's existing Appearance preference. This separation lets the runtime be tested without altering gameplay configuration.

No OneConfig dependency, Kotlin plugin, Compose runtime, Skiko native or Polyfrost source was added.
