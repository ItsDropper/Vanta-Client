# ADR-001: Vanta-native retained UI framework

- **Status:** Accepted for prototype; production adoption gated on in-game validation.
- **Date:** 2026-11-02
- **Decision owners:** Vanta Client maintainers

## Context

Vanta Client targets Minecraft 1.21.11, Fabric Loader 0.19.3, Fabric API 0.141.4+1.21.11 and Java 21. The project is Java-only and currently renders screens with Minecraft's `Screen`, `GuiGraphics` and `AbstractWidget` APIs.

The current GUI has multiple independent drawing, animation, scaling and input implementations. It also has an existing feature/configuration ecosystem that must remain functional during migration.

The goal is a reusable declarative component/layout foundation that remains native to Minecraft's screen lifecycle and does not require a second rendering backend.

## Options considered

### A. Integrate OneConfig / Polyfrost UI modules

**Advantages**
- Mature configuration, HUD and UI concepts.
- Current OneConfig source is modular.
- OneConfig lists support for Minecraft 1.21.11.
- Existing examples demonstrate mod settings registration and common configuration controls.

**Disadvantages**
- OneConfig is a full configuration ecosystem, not merely a rendering widget.
- Current module dependencies include Kotlin/Compose, PolyUI, LWJGL and version-specific Minecraft integration.
- OneConfig is licensed LGPL-3.0 plus additional terms; adoption adds legal and distribution review.
- Vanta already has its own public addon settings API and configuration persistence. Replacing that boundary would create unnecessary product coupling.
- The public evidence does not establish that PolyCompose alone is a supported, self-contained Minecraft screen host.

**Decision:** Do not integrate as the Vanta runtime. Continue studying its separation of concerns and interaction patterns.

### B. Integrate Compose Multiplatform directly

**Advantages**
- Strong declarative composition and state model.
- Rich layout, text, animation and accessibility primitives.
- Desktop JVM support and Skia-backed rendering through Skiko.

**Disadvantages**
- Requires introducing Kotlin and Compose compiler/plugin/toolchain integration into a Java 21 Fabric project.
- Compose Desktop/Skiko is not automatically a Minecraft renderer. A correct Minecraft host still needs to bridge render lifecycle, input, focus, GUI scaling, clipping, resource lifetime and GPU presentation.
- OneConfig's deployment demonstrates a substantial dependency bundle with platform-specific Skiko natives and Fabric Language Kotlin.
- Additional runtime and native-library maintenance is disproportionate for one client mod's GUI.

**Decision:** Do not introduce Compose for this project at this stage. Reconsider only if the native prototype proves materially incapable of meeting the visual or interaction requirements.

### C. Build a lightweight Vanta-native declarative framework on Minecraft GUI APIs

**Advantages**
- Uses the exact rendering and input APIs already present in the target runtime.
- No new runtime dependencies or Kotlin migration.
- Keeps Minecraft screen lifecycle, GUI scale, text, narration and native widgets available.
- Enables Vanta-owned layout, component, theme, input and animation contracts.
- Can migrate one surface at a time while old screens remain operational.

**Disadvantages**
- Vanta must implement and test layout, focus, scrolling, clipping, component lifecycle and state invalidation.
- Visual primitives are constrained by the Minecraft GUI rendering API unless carefully extended.
- Framework scope must be controlled; recreating all of Compose would be wasteful.

**Decision:** Selected.

## Decision

Build a small retained-mode UI runtime in Java on top of Minecraft's `GuiGraphics` and screen/input lifecycle.

The runtime will be declarative at the screen construction level but retained for layout and interaction. It will not attempt to reproduce the entire Compose programming model.

### Proposed boundaries

1. **UI model:** immutable or explicitly-owned component descriptions and layout parameters.
2. **Layout:** measure/place pass for row, column, box, overlay, padding, constraints and scroll viewport.
3. **Runtime tree:** stable node identity, parent/child ownership, hit testing, clipping and lifecycle.
4. **Input:** one routed event path for pointer, wheel, keyboard and focus; coordinates normalized once into logical GUI units.
5. **Rendering:** Minecraft-native draw adapter, ordered layers, scoped clipping and shared text helpers.
6. **State:** explicit observable values or callbacks with subscriptions scoped to a screen session; no hidden global mutable UI state.
7. **Theme:** centralized tokens for color, typography, spacing, radii, component dimensions and motion.
8. **Minecraft adapter:** a thin `Screen` host that owns the UI session and forwards lifecycle/input/render calls.

Existing settings/configuration models remain authoritative. UI controls bind to them through adapters; the framework must not create a second persistence system.

## Prototype acceptance gates

The prototype must run inside Minecraft 1.21.11 and demonstrate navigation, scrolling, text, button, toggle, slider, dropdown, tooltip, keyboard/mouse input and theme switching. It must pass GUI-scale/resolution/input checks before any existing screen is migrated.

A source-only mock or screenshot generated outside Minecraft does not satisfy this gate.

## Risks and mitigation

| Risk | Mitigation |
|---|---|
| Input coordinate mismatch | Normalize coordinates at the Minecraft host boundary; test all GUI scales and window sizes. |
| Clipping leaks | Use nested clip scopes and hit-test against the same ancestor clip chain. |
| Excess allocations per frame | Retain nodes and layout data; update only on state/layout invalidation; profile before optimizing. |
| State leaks across screens | Own subscriptions and animation state by UI session; dispose on screen close. |
| Migration breaks settings | Keep existing models/getters/setters and introduce adapters; migrate one screen at a time. |
| Framework becomes overbuilt | Limit initial API to demonstrated Vanta needs; no compiler plugin, reflection DSL or general-purpose effect runtime. |
| Visuals remain mediocre | Capture actual in-game screenshots and use repeatable review criteria before migration. |

## Dependency and license decision

No new dependency is introduced by this ADR. Minecraft/Fabric APIs remain the runtime foundation. OneConfig/Compose source is used for research only; no code or assets are copied.

## Expected implementation cost

This is a multi-stage change. The first deliverable is an isolated in-game prototype, not a wholesale migration. Production adoption depends on successful runtime validation and measured rendering/input behavior.
