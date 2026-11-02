# ADR-001: Vanta-native retained UI framework

- **Status:** Accepted for prototype implementation
- **Date:** 2026-11-02
- **Decision owners:** Vanta Client maintainers
- **Target baseline:** Minecraft 1.21.11, Fabric, Java 21
- **Audit baseline:** \`main\` at \`c1d2569a71c3f78f01a8e13a456865f8d4a08789\`

## Context

Vanta is a Java-only Fabric client mod using Minecraft's \`Screen\`, \`GuiGraphics\`, \`AbstractWidget\` and Fabric APIs. Its active settings/mod-library surface, Modrinth browser, legacy ClickGUI and HUD editor use separate rendering/layout/input patterns.

The desired outcome is a reusable and visually coherent UI foundation, not a wholesale adoption of a different product's interface.

OneConfig/PolyCompose demonstrates what a mature declarative UI integration can achieve, but its Minecraft integration is a large system: Compose runtime scheduling, scene lifetime, input/clipboard bridging, layout, font and graphics registries, a custom drawing backend, render-pipeline integration, cached geometry and resource recovery. Its public UI entry point is primarily a bridge to OneConfig's own screen, not a lightweight arbitrary-screen host. The old documented OneConfig element API is explicitly discouraged by its maintainers.

## Decision

**Choose approach C: build a small Vanta-native retained/declarative UI framework on top of the existing Minecraft GUI APIs.**

Keep Minecraft's \`Screen\` as the host lifecycle and use \`GuiGraphics\` for drawing. Keep the Minecraft GUI/render pipeline responsible for final rendering. Use native \`AbstractWidget\`/widget focus, input dispatch and narration where they fit; place them through Vanta's layout system rather than making each screen position them independently.

The framework is intentionally smaller than Compose. It owns the parts Vanta needs consistently:

- A retained UI tree with explicit measure, arrange, render and input phases.
- Row, column, box and overlay/stack layout primitives.
- Padding, alignment, spacing, size constraints and responsive sizing.
- Scroll viewports with a defined clip/input boundary.
- Shared text, button, toggle, slider, dropdown, search, tab, tooltip, dialog, notification and progress components.
- Shared focus/hover/pressed/disabled interaction states.
- Typed state bindings to existing feature, configuration and integration setting models.
- A centralized theme/token provider backed by existing Appearance preferences.
- Time-based animation state updated from screen ticks/frame delta, not unbounded per-render interpolation.
- Screen-scoped resource ownership and cleanup.

Do not use a new OpenGL renderer or framebuffer compositor for normal UI components. Do not duplicate Minecraft's font renderer, text shaping, widget focus traversal or input layer when native support is adequate.

## Alternatives

### A. Integrate OneConfig and its Compose UI

**Advantages**
- OneConfig already supports Fabric builds for Minecraft 1.21.11.
- It offers a real Compose-based UI and theme system.
- It has an existing shared configuration ecosystem and HUD concepts.

**Reasons rejected**
- OneConfig is a standalone configuration mod, not a neutral widget package. Its documented public UI bridge opens or creates OneConfig's own screen; it does not give Vanta a simple, stable Java host for an independent Vanta design system.
- Its old element API is explicitly discouraged in upstream documentation.
- Adopting it requires Compose Multiplatform and Fabric Language Kotlin as runtime dependencies, plus the appropriate OneConfig integration/distribution and compatibility lifecycle.
- OneConfig source is LGPLv3 with additional Polyfrost terms. The inspected terms include visible attribution obligations for adaptations and trademark restrictions, with stated exceptions. This conflicts with Vanta's independent product identity unless separately reviewed and accepted.
- Vanta would be dependent on upstream release cadence and OneConfig's internal host/bridge behavior.

### B. Integrate Compose Multiplatform directly

**Advantages**
- Mature declarative composition and state model.
- Rich layout, text, semantics, focus, pointer input and animation abstractions.
- Apache-2.0 for JetBrains-authored repository code.

**Reasons rejected for this project**
- Compose Multiplatform's desktop target is not a Minecraft Screen integration. It uses the Skiko/Skia platform stack; an in-game host still needs explicit scene creation, event translation, clipboard, density/surface-scale handling, render-thread scheduling, clipping, texture lifetime and Minecraft render-pipeline integration.
- OneConfig's implementation is evidence of the size of this adapter layer, not evidence that the adapter is free.
- Vanta currently has no Kotlin plugin/runtime and no Compose dependencies. Adding them increases build complexity, modpack/runtime dependencies and maintenance surface for a UI used only inside one game client.
- Compose compiler/plugin/runtime compatibility must be kept aligned across releases. That is unnecessary operational cost for this scope.

### C. Vanta-native retained framework

**Advantages**
- Uses the exact Minecraft/Fabric/Java baseline already in the repository.
- No third-party runtime dependencies, shading or Kotlin migration.
- Native GUI scaling, text rendering, input events and widget lifecycle remain authoritative.
- Vanta controls the public API, visual language, release cadence and resource lifecycle.
- Easier to keep feature/config/HUD behavior independent from the UI implementation.

**Costs and risks**
- Vanta must implement and test layout, component behavior and state synchronization itself.
- A custom retained tree can become an overbuilt Compose clone if scope is not controlled.
- The native renderer constrains effects compared with a bespoke Skia backend.

**Mitigation**
- Limit the framework to the components and layout behaviors used by Vanta.
- Reuse Minecraft's widget/input/text/rendering facilities.
- Keep layout measurement deterministic and cache results until content or constraints change.
- Keep component state scoped to screen/session lifetimes; persist settings only through existing configuration models.
- Add focused geometry/interaction tests and verify the prototype inside the actual Minecraft client before migrating the current screens.

## Design constraints

1. The framework must not own business logic for features, Modrinth downloads, arbitrary mod loading or HUD element behavior.
2. The existing \`SettingDefinition\`, \`Feature\`, config and preferences contracts remain the source of truth.
3. Mouse coordinates are interpreted in the same GUI-scaled coordinate space used by Minecraft's \`Screen\`; the framework must not scale widget positions twice.
4. Layout is invalidated by content/constraint/viewport changes, not recomputed with fresh object graphs each frame.
5. Animation uses bounded elapsed time and respects the existing animations preference.
6. Scrolling has both a render clip and an input hit-test clip; off-viewport controls must not receive clicks.
7. Theme tokens provide every semantic component color, including focused, hovered, selected, disabled and error states.
8. HUD elements remain normalized to screen position and remain renderable during gameplay without a screen host.
9. All screen-owned textures/listeners/registrations must be released on the corresponding lifecycle end.
10. No existing screen is replaced until the standalone prototype passes its acceptance criteria.

## Prototype acceptance gates

The prototype must run as an in-game Minecraft screen and demonstrate:

- A centered/responsive main window with navigation and scrollable content.
- Text, button, toggle, slider, dropdown, search, tooltip and tab primitives.
- Focus and keyboard navigation/activation.
- Pointer hover/click/drag, wheel scrolling and clipping.
- Animated state transitions and theme switching.
- Representative Vanta settings: FPS information, render-distance control example, crosshair/HUD options, keybind placeholder/adapter and performance preferences.
- Correct coordinate behavior after GUI-scale and window-size changes.
- No input capture or focus side effects after closing.

The prototype is not accepted on code inspection alone. It requires runtime interaction and screenshot review.

## Consequences

No Compose, Kotlin or OneConfig dependency is added. The project keeps Minecraft's renderer and screen lifecycle as its platform boundary. The first implementation milestone is isolated: framework primitives and a prototype screen only. Existing product screens remain in place until that milestone is validated.
