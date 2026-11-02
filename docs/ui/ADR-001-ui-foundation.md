# ADR-001: Vanta-native retained UI framework

- **Status:** Accepted for prototype; production adoption gated on in-game validation.
- **Date:** 2026-11-02
- **Target:** Minecraft 1.21.11 / Fabric / Java 21

## Context

Vanta's active settings screen is \`VantaIntegrationsScreen\`; the old \`ClickGuiScreen\` redirects to it. Vanta currently uses Minecraft \`Screen\`, \`GuiGraphics\` and \`AbstractWidget\`, but layout, rendering, animation and input behavior are distributed across screens and individual controls.

The goal is a reusable declarative UI foundation that preserves the existing feature/configuration model and remains native to Minecraft's screen lifecycle.

## Options considered

### A. Integrate OneConfig / Polyfrost UI

**Advantages**
- The current OneConfig \`v1\` source includes Minecraft 1.21.11 integration.
- Its Compose-based UI has a mature retained component tree, Row/Column/Box layout, input routing, focus handling, clipping, invalidation and cached rendering.
- It provides strong evidence for the architecture needed to reach a polished client UI.

**Disadvantages**
- PolyCompose is not a drop-in Minecraft screen host. OneConfig's Minecraft \`ComposeScreen\` creates and manages a Compose scene, frame recomposer, platform context, renderer, input adapters, clipboard, scaling, failure recovery and resource lifecycle.
- PolyCompose installs custom platform graphics/text backends, owns a custom node/layout runtime and relies on Polyfrost rendering integration.
- \`OneConfigUI.createScreen()\` creates a OneConfig-owned screen through a service-loaded bridge; it is not a generic Vanta composable-screen factory. \`OptionWidgets.refresh(Property)\` is a narrow property refresh API.
- The dependency graph adds Kotlin, Compose runtime/Foundation/UI, coroutines, Fabric Language Kotlin, Skiko/AWT and native runtime packaging.
- OneConfig is LGPLv3 plus Polyfrost Additional Terms, with project-specific branding/visible attribution implications requiring review.
- Vanta already owns a public typed settings API and configuration persistence.

**Decision:** Do not use OneConfig as Vanta's runtime dependency. Use its architectural lessons as research. Do not copy OneConfig source, visual assets or branding.

### B. Integrate Compose Multiplatform directly

**Advantages**
- Powerful declarative state, text, layout and animation primitives.
- Desktop JVM Compose provides a mature component system.

**Disadvantages**
- Vanta would still need a custom Minecraft screen host and rendering backend. Compose Desktop does not automatically bind Minecraft's render lifecycle, input events, GUI scaling, clipping, focus or GPU resource ownership.
- Introducing Compose requires Kotlin Gradle/compiler integration and a runtime bundle. OneConfig's implementation demonstrates the additional host/backend work this entails.
- This would be a disproportionate runtime/toolchain change for a Java-only client mod.

**Decision:** Do not add Compose at this stage. Revisit only if the Vanta-native prototype cannot meet demonstrated visual/interaction requirements.

### C. Build a lightweight Vanta-native retained/declarative framework

**Advantages**
- Uses the exact Minecraft/Fabric/Java 21 runtime already present.
- Keeps Minecraft screen lifecycle, GUI coordinate system, text and native drawing primitives.
- No new runtime dependency or Kotlin migration.
- Preserves Vanta-owned feature, addon and configuration APIs.
- Enables deliberate migration one interface at a time.

**Disadvantages**
- Vanta must implement and validate layout, input routing, focus, clipping, scrolling, state subscriptions and component lifecycle.
- The team owns the rendering/interaction bugs and future Minecraft mapping changes.
- Scope must be limited; rebuilding all of Compose would be wasteful.

**Decision:** Selected.

## Decision

Build a small retained UI tree with declarative node construction on top of Minecraft GUI APIs. The first implementation is deliberately limited to Vanta's current needs; it is not a reimplementation of the Compose compiler, effect system or coroutine runtime.

### Required layers

1. **Design tokens:** surfaces, border/text hierarchy, accent, spacing, dimensions, typography and motion.
2. **Node tree:** stable parent/child ownership and component-local state.
3. **Layout:** measure/arrange for Row, Column and Box/Overlay; preferred/min/max constraints, spacing and flex distribution.
4. **Rendering:** Minecraft-native \`GuiGraphics\`, ordered layers and scoped clipping.
5. **Input:** one coordinate convention; pointer routing, capture/release, keyboard focus, activation and scroll routing.
6. **State adapters:** callbacks/suppliers that bind to existing settings/configuration without creating a parallel persistence system.
7. **Screen host:** thin Minecraft \`Screen\` wrapper that creates, ticks, renders and disposes one UI runtime.

### Prototype boundary

\`VantaUiPrototypeScreen\` is isolated and opened from Appearance settings. It demonstrates navigation, Row/Column/Stack layout, live FPS text, toggle, slider, dropdown, tooltip, mouse/keyboard focus, scroll clipping and accent switching. FPS is a live Minecraft value; the prototype's render-distance, crosshair, HUD and keybind examples are intentionally local demo state. The animation toggle binds to the existing Appearance preference.

This prototype must be launched in Minecraft 1.21.11 and tested across GUI scales/resolutions before existing screens are migrated.

## Dependencies and licenses

No new dependency is introduced. The prototype uses existing Minecraft/Fabric APIs. No OneConfig or Compose code/assets were copied.

## Risks and mitigation

| Risk | Mitigation |
|---|---|
| Mouse/layout coordinate mismatch | Normalize at the Screen boundary; test GUI scale and resize. |
| Clip/hit-test mismatch | Keep layout clip and pointer hit bounds on the same ancestors. |
| Scroll offset stale after wheel | Invalidate layout when scroll state changes. |
| Focus or pointer capture leaks | Keep them inside the runtime; dispose on screen removal and clear active drags on release. |
| Settings regressions | Bind existing models through adapters; migrate one screen at a time. |
| Render overhead / allocations | Retain nodes; update layout only when invalidated; profile when local runtime access exists. |
| Framework overgrowth | Add primitives only when a Vanta screen needs them and acceptance tests prove them. |
| Visual quality underperforms | Use real Minecraft screenshots, identify design causes and iterate before migrating production screens. |

## Acceptance gates

The prototype is not production-complete until the actual Minecraft 1.21.11 build succeeds and manual runtime checks cover:
- Correct pointer mapping and control interaction.
- Keyboard Tab/Enter/Space/arrow behavior.
- Scroll clipping and tooltip placement.
- Multiple GUI scales, window sizes and aspect ratios.
- Repeated open/close and resize without stale state.
- No input interference after the screen is closed.

A source inspection or green CI build alone does not establish visual quality or gameplay performance.
