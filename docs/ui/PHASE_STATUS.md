# Vanta UI Framework — Phase Status

**Updated:** 2026-11-02  
**Branch:** \`main\`

This is the project status ledger for the UI framework work. A source implementation or successful compilation is not equivalent to in-game validation.

## Phase 0 — Repository and environment audit

<badge>Completed: source audit</badge>

Verified the repository's Minecraft 1.21.11, Fabric Loader 0.19.3, Fabric API 0.141.4+1.21.11 and Java 21 baseline; identified the actual active settings screen, legacy ClickGUI redirect, HUD editor, setting registry and Appearance preference model.

Deliverable: [Architecture audit](ARCHITECTURE_AUDIT.md), with the deeper original repository review at [\`docs/architecture/2026-11-02-ui-framework-audit.md\`](../architecture/2026-11-02-ui-framework-audit.md).

**Not claimed:** exhaustive execution tracing or local game runtime inspection.

## Phase 1 — OneClient / Polyfrost research

<badge>Completed: source research</badge>

Inspected the current OneConfig \`v1\` source, including PolyCompose runtime/layout, ComposeScreen, renderer integration and OneConfig public UI bridge. Documented the distinction between current Compose-based Minecraft rendering and older NanoVG documentation, plus the relevant dependency and license implications.

Deliverable: [OneClient research](ONECLIENT_RESEARCH.md).

## Phase 2 — Architecture selection

<badge>Completed: ADR</badge>

Selected a Vanta-native retained/declarative framework on Minecraft's existing GUI APIs. Compose/OneConfig are research references, not runtime dependencies.

Deliverable: [ADR-001](ADR-001-ui-foundation.md).

## Phase 3 — Isolated in-game prototype

<badge color="warning">Source and CI progress; runtime validation pending</badge>

An isolated \`VantaUiPrototypeScreen\` is wired from Vanta Client > Settings > Appearance > **Open UI Prototype**.

Current source demonstrates:
- Row, Column, Stack/Box/Overlay, padding, spacing, flex, constraints, alignment and justification.
- Scrolling, clipping, matched clip/hit-test bounds and dropdown reveal within scrollable content.
- Text, bounded paragraphs, buttons, selected navigation, toggles, sliders, expanded dropdowns, keyboard-editable text fields and tooltips.
- Mouse routing, slider pointer capture, Tab/Shift+Tab focus, Enter/Space activation, arrow adjustment, Escape dismissal and text editing/caret placement.
- Theme accent and animation preference bindings.
- Responsive sidebar-to-tab navigation at narrow widths.
- Live FPS and smoothed UI CPU command-submission timing (GPU execution excluded).

The render-distance, crosshair, HUD, keybind and search examples are demo state. They do not mutate gameplay settings. The FPS readout and Appearance preference controls use real Vanta/Minecraft values.

**Acceptance still pending:** launch inside Minecraft 1.21.11; exercise input, GUI scale, multiple resolutions/aspect ratios, repeated open/close, clipping, expanded dropdowns and focus; capture and inspect actual screenshots. The connected repository workflow can compile the project, but cannot establish those manual runtime properties.

## Phase 4 — Reusable framework

<badge color="warning">Core base implemented; component library incomplete</badge>

Current code:
- \`VantaUiTheme\`
- \`VantaUiNode\`
- \`VantaUiRuntime\`

The initial core includes retained layout, input routing, state suppliers/setters, scroll/clip/focus handling and basic controls.

Still to build after prototype acceptance:
- Icons and richer text styles.
- Reusable dialogs, notifications and progress components.
- Keybind editor, color picker and other specialized controls.
- Better scrollbar interaction and any additional composition/state invalidation needed by real screens.
- A broader component test suite.

## Phase 5 — Design system

<badge color="warning">Initial tokens documented and implemented; visual approval pending</badge>

Initial palette, spacing, dimensions, typography rules, surface hierarchy, interactive states and motion policy exist in \`VantaUiTheme\` and [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md).

**Not claimed:** approved visual quality. A design system must be reviewed in the actual Minecraft renderer at more than one GUI scale.

## Phase 6 — Production screen migration

<badge>Not started</badge>

No production GUI has been replaced. \`VantaIntegrationsScreen\`, legacy ClickGUI compatibility paths, HUD editor and existing feature/configuration logic remain in place.

Migration is gated on Phase 3 in-game acceptance.

## Phase 7 — Visual QA

<badge>Pending in-game execution</badge>

No actual Minecraft screenshots have been captured or visually inspected in this work session. No screenshot path is claimed. Use [PROTOTYPE.md](PROTOTYPE.md) for the manual test matrix.

## Phase 8 — Functional and performance QA

<badge color="warning">CI compilation only; runtime tests pending</badge>

Multiple repository GitHub Actions runs have passed the JDK 21 Gradle build for intermediate framework revisions. The newest changes must pass the latest run before being described as current-green.

No in-game FPS comparison, GPU profile, allocation profile or multi-resolution manual test has been performed. The prototype's CPU submission timer is an instrument, not a performance result.

## Current stop condition

Do not migrate existing production screens until the prototype is run in Minecraft 1.21.11 and its input, scaling, clipping, lifecycle and screenshots have been reviewed. This is a deliberate quality gate, not a claim of completion.
