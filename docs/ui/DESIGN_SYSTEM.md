# Vanta UI Design System

**Status:** redesigned prototype implemented; screenshot review and in-game interaction validation remain open.

This document defines the design contract for new framework components. Components must consume these tokens and rules rather than inventing per-screen colors, dimensions or animations.

## 1. Product identity

Vanta's interface should feel like a focused desktop control surface, not a generic dashboard or a Minecraft mod menu assembled from default widgets.

Design priorities, in order:

1. Information hierarchy and scan speed.
2. Precise alignment and consistent control geometry.
3. Legible typography using the bundled IBM Plex Sans family through Minecraft's font provider.
4. A restrained accent used for selection, progress and action.
5. Deliberate depth through surface contrast, not excessive borders/glow.
6. A consistent vector-style icon language and motion that communicates state without delaying input.

Do not solve poor composition by adding more cards, gradients, blur or corner radius.

## 2. Color tokens

The initial palette is defined in \`VantaUiTheme\`.

| Token | Initial value | Use |
|---|---:|---|
| Canvas | \`#0B0F16\` | Root screen background |
| Panel | \`#111823\` | Primary surface |
| Panel raised | \`#151E2B\` | Secondary panel |
| Surface | \`#192332\` | Controls and interactive rows |
| Surface hover | \`#202E40\` | Hovered control surface |
| Border | \`#2A394C\` | Structural separation |
| Border subtle | \`#202B3A\` | Low-emphasis tracks/dividers |
| Text primary | \`#EAF0F8\` | Titles and important labels |
| Text secondary | \`#9AA8BA\` | Descriptions and values |
| Text muted | \`#68788D\` | Hints and metadata |
| Track | \`#303D50\` | Slider/switch inactive track |
| Accent | Appearance preference | Selection, active state, progress, focus |

Accent is sourced from \`VantaClientPreferences\`. New components must not hardcode their own blue/green/purple accent.

Avoid using accent as a large background unless the control is a primary action or a strongly selected navigation item. Do not use color as the sole state indicator.

## 3. Spacing and dimensions

The framework exposes a 4 px spacing rhythm:

| Token | Value |
|---|---:|
| SPACE_1 | 4 px |
| SPACE_2 | 8 px |
| SPACE_3 | 12 px |
| SPACE_4 | 16 px |
| SPACE_5 | 20 px |
| SPACE_6 | 24 px |

Initial component dimensions:
- Standard control height: 32 px.
- Standard content row: 42 px.
- Slider row: 46 px.
- Root screen inset: 22 px.
- Component hit bounds must include the visible control, not only its text or decorative center.

These are logical Minecraft GUI coordinates. They must not be multiplied by physical framebuffer scale inside individual components.

## 4. Typography

Text remains rendered and measured by Minecraft's active `Font` renderer, but uses a bundled IBM Plex Sans variable TTF through Minecraft's normal font-provider system. The font is distributed under the SIL Open Font License 1.1; the license is included next to the asset.

- `vanta:ui`: 9 px, standard controls and body text.
- `vanta:title`: 14 px, page titles.
- `vanta:caption`: 8 px, eyebrow labels and metadata.

All text measurement, truncation and wrapping must use the same styled `Component` font as drawing. A default-font measurement paired with a custom-font draw is a layout bug.

### Iconography

Use the framework's vector-style icon set for navigation, section identity and common actions. Icons are drawn with `GuiGraphics` primitives at logical GUI scale; do not use Unicode symbols, emoji or platform-dependent glyph substitutions as UI icons. New icons must share a consistent stroke weight, geometry and 16 px base grid.

### Text hierarchy
- Page title: display text, short and clear.
- Section heading: primary text, separated by spacing rather than a heavy card.
- Control label: primary text.
- Description: secondary text, optional and concise.
- Metadata/hint: muted text.
- Live values: secondary text, right-aligned to the control where practical.

Do not render descriptions at the same contrast as control labels. Clamp long content or provide a scroll path; never allow it to silently overlap controls.

## 5. Surfaces and depth

Use a limited stack:
1. Canvas.
2. Main panel.
3. Raised section.
4. Interactive surface.
5. Focus/selection accent.

A border is structural, not decoration. Most controls should not need a full bright outline in every state. Prefer a subtle surface change plus a deliberate focus indicator.

## 6. Interactive states

Every interactive component must define:
- Default.
- Hover.
- Focused (keyboard focus, not just mouse hover).
- Pressed/active where applicable.
- Disabled.
- Value-on/value-off where applicable.

Hover and focus are separate. Keyboard users must receive a visible focus treatment. Disabled controls must not respond to mouse, keyboard or drag events.

## 7. Motion

The global \`animations\` Appearance preference is the motion authority.

- Animate only state feedback, navigation or transitions that communicate a real change.
- Do not use animation as a substitute for layout.
- Use time/frame-based motion rather than assuming a stable frame rate.
- Stop/cancel screen-owned animation state when its screen is removed.
- With animations disabled, controls should snap directly to the target state.
- Never delay a click or make an input target move away from the pointer.

The first prototype uses short interpolated hover and switch transitions. Motion curves/durations remain provisional until in-game review.

## 8. Layout and input contract

- Build screens from Row, Column and Stack containers.
- Layout is expressed in logical GUI coordinates.
- Measure and arrange before draw.
- Use flex only for intentionally expandable children.
- Use min/max constraints to prevent controls from becoming unusably small.
- Scrollable content owns its own scroll offset and clip viewport.
- The clip viewport and pointer hit-test viewport must be identical.
- Pointer coordinates arrive in Minecraft GUI-scaled units and are never converted again in child components.
- A screen owns one runtime. Removing the screen disposes focus and pointer capture.
- Render and input must traverse the same retained node tree.

## 9. Component responsibilities

- **Text:** render and measure styled text; no input handling.
- **Icon:** draw a named vector glyph from the shared icon set; no input handling.
- **Button:** activate a command; does not own persistent settings.
- **Toggle:** read and write a boolean via supplier/setter.
- **Slider:** map pointer/key input to validated numeric range and step.
- **Dropdown:** select one value from a supplied option set.
- **Text field:** edit a bounded string through keyboard character/key events.
- **Container:** layout and clip children; does not own feature business logic.

Settings models and persistence remain outside the UI runtime. A UI node receives a getter/supplier and setter/callback; it must not write JSON or mutate feature registries directly.

## 10. Deliberate exclusions

The first framework does not include a full Compose-like effect system, recomposition compiler, coroutine model, general purpose modifier DSL, Skia renderer or a second GPU backend. Add capabilities only when a Vanta screen requires them and the simpler native implementation has been measured.
