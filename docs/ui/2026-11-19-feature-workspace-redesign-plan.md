# Vanta feature workspace redesign — engineering plan

**Date:** 2026-11-19  
**Baseline:** `main` at `49915528cf48571bffd939034d124ea45357ca2b`  
**Target:** Minecraft 1.21.11 / Fabric / Java 21  
**Scope:** In-game feature workspace and the reusable VantaCompose/layout architecture it depends on.

## Objective

Replace the current settings-first, vertically stacked feature list with a client-style feature browser: category navigation, searchable/filterable module cards, clear enabled/disabled states, direct configuration entry, and a responsive card grid. The target visual language takes cues from OneClient's Compose-driven in-game UI and Lunar's discoverable mod cards, without copying their assets or implementation.

This is not a request to reskin a few controls. The screen's information architecture and positioning model need to make the feature collection the primary surface.

## Evidence-based audit

### Current ownership and runtime

- `VantaSettingsScreen` owns the category and module browsing experience.
- `VantaUiRuntime` owns retained-node layout, scale transforms, hit-testing, focus, pointer capture, scrolling, dropdown overlays and frame animation.
- `VantaUiNode` is the retained tree and currently supports row/column/stack containers, flex sizing, fixed dimensions, scrollable containers, text, icons, images, buttons, toggles, sliders, dropdowns and text fields.
- Feature state and settings remain owned by `FeatureRegistry`, `Feature`, `SettingRegistry`, and the existing config persistence path.
- The current category view is still a full-width vertical list. Its header and search affordance precede rows with inline configure/toggle controls. It does not expose a card grid or a deliberate card hierarchy.
- The previous overview redesign does not solve the user's core request: the category/module browser is the main daily interaction and needs the architectural attention.

### Reference findings

- OneConfig's active Minecraft UI path uses a Compose screen host, a dedicated runtime/recomposer, layout/measure passes, a renderer adapter, explicit input forwarding and lifecycle cleanup. PolyCompose is not merely a set of widgets.
- Lunar's documented mod menu emphasizes per-mod identity, icons, descriptions, enabled state, settings access, compact mode and discoverability. Its later UI also added favoriting to prioritize frequently used mods.
- Vanta should adopt these product and architecture principles, not copy proprietary visuals or import OneConfig's dependency/runtime stack without a separate compatibility and licensing decision.

## Proposed information architecture

1. **Persistent shell**
   - Compact Vanta brand/header strip.
   - Left category rail on wide viewports; compact horizontal category selector when space is constrained.
   - Main content viewport with independent scrolling.
   - Search and sorting/filter controls grouped with the category title, not detached from the content they affect.

2. **Feature browser**
   - Responsive two/three-column card grid depending on available logical width.
   - Each card has a stable hierarchy: category/icon identity, feature name, concise description, enabled state, and a configuration affordance only when the feature has settings.
   - Whole-card hover/focus feedback; explicit action targets remain keyboard reachable.
   - Enabled cards have a restrained accent/state treatment; disabled cards remain legible rather than being visually erased.
   - Search results and empty states remain in the same grid region.
   - Sorting is deterministic and does not mutate feature registration order.

3. **Feature details**
   - Selecting Configure opens a focused detail view with a clear back path, feature status, description and settings grouped in a material surface.
   - Existing setting adapters and persistence remain the source of truth.

4. **Overview**
   - Becomes a useful dashboard entry point, but it does not replace the module browser.
   - Show category shortcuts, active module summary and a small set of enabled/favorited modules only where backed by real state.

## Layout and rendering requirements

- Use Minecraft GUI logical coordinates consistently; the runtime remains the only owner of the preferred-scale and GUI-scale conversion.
- Derive card column count and card minimum width from the measured content viewport. Do not hard-code a fixed three-column layout.
- Keep the grid inside the existing retained layout/hit-test/scroll model; cards must clip and scroll as one viewport.
- Avoid nested scroll containers unless their gesture ownership is explicit.
- Use actual node bounds for hover and click; no independent screen-level coordinate math.
- Preserve focus traversal and pointer capture. New controls must be reachable by keyboard and must not steal slider/scrollbar drags.
- Reuse shared theme tokens and VantaCompose factories. Keep expensive blur/backdrop work at screen strata rather than issuing it per card.
- Keep frame animation delta-time based and avoid per-frame allocations or repeated feature/settings scans where cached category data is sufficient.

## Implementation sequence

### Phase A — foundation
- Add this plan before implementation.
- Audit card layout constraints, container measurement, clipping and node hit-testing.
- Add a small set of card-oriented component factories and only the layout primitives required by the grid.

### Phase B — feature browser
- Replace the vertical module rows with a responsive card grid.
- Add meaningful card interactions: enable/disable, configure, focus/hover, and stable search results.
- Keep category navigation and settings routing intact.
- Ensure the compact layout does not overflow or turn cards into tiny columns.

### Phase C — verification and refinement
- Run the GitHub build workflow.
- Run the real Minecraft screenshot workflow and inspect captured output, not merely workflow status.
- Check normal and compact layouts and GUI scale 1–4 at the workflow's supported viewport sizes.
- Fix actual defects found in screenshots/logs and add targeted layout tests for the new grid behavior.
- Review the final diff for unrelated backend changes and incomplete actions.

## Acceptance criteria

- The feature browser is visibly a card-based workspace, not a row list with rounded backgrounds.
- Cards have consistent hierarchy, sizing, spacing and interaction states.
- Column count responds to available width; no horizontal clipping at supported viewport sizes.
- Search, enable/disable, configure and persistence continue to use existing feature APIs.
- Mouse and keyboard navigation both work.
- Build passes and screenshot artifacts are actually inspected before calling the visual pass verified.
- No fake module metadata, fake settings, placeholder actions or new runtime dependencies.

## Explicit non-goals

- Rewriting feature implementations or their configuration model.
- Replacing the existing Java retained runtime with JetBrains Compose as part of this visual pass.
- Rebuilding the HUD editor, Modrinth browser or integrations library before the feature browser is sound.
- Claiming parity with OneClient/Lunar based only on screenshots or marketing descriptions.
