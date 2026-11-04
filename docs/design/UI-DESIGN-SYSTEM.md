# Vanta UI Design System

**Status:** Superseded implementation brief. The live component/token contract is maintained in [`docs/ui/DESIGN_SYSTEM.md`](../ui/DESIGN_SYSTEM.md), and current migration/verification status is maintained in [`docs/ui/PHASE_STATUS.md`](../ui/PHASE_STATUS.md).  
**Target:** Minecraft 1.21.11 GUI-scaled coordinates.  
**Brand direction:** technical, quiet, dark, precise; not a generic SaaS dashboard.

## 1. Visual identity

Vanta should feel like a high-quality desktop utility rendered inside Minecraft: compact but breathable, deliberate information hierarchy, restrained color, stable geometry and crisp typography.

The interface should rely on composition and hierarchy rather than decorative effects. Avoid glass blur, heavy gradients, random glow, repeated nested cards and large empty dashboard panels.

## 2. Color tokens

Components consume semantic tokens from one theme object. A component must never define its own accent hex value.

| Token | Default | Purpose |
|---|---:|---|
| \`surface.canvas\` | \`#0C1017\` | Screen-level backdrop |
| \`surface.panel\` | \`#111823\` | Main application shell |
| \`surface.sidebar\` | \`#0F151F\` | Navigation rail |
| \`surface.raised\` | \`#192332\` | Popup, selected row and raised control |
| \`surface.input\` | \`#0B111A\` | Text/search input background |
| \`surface.hover\` | \`#202C3B\` | Hovered surface |
| \`surface.pressed\` | \`#26364A\` | Pressed surface |
| \`border.subtle\` | \`#253142\` | Structural separators |
| \`border.strong\` | \`#34445A\` | Focus-adjacent and emphasized outlines |
| \`text.primary\` | \`#E9EFF8\` | Main labels and headings |
| \`text.secondary\` | \`#9AA8BA\` | Descriptions and metadata |
| \`text.muted\` | \`#6F7D90\` | Hints and low-priority metadata |
| \`text.disabled\` | \`#566273\` | Disabled controls |
| \`accent.primary\` | Existing Appearance accent | Selection, focus, active control state |
| \`accent.soft\` | Derived from primary with bounded alpha | Subtle active surfaces |
| \`state.success\` | \`#64B994\` | Successful operation status |
| \`state.warning\` | \`#D9A85B\` | Caution status |
| \`state.danger\` | \`#D97878\` | Destructive/error status |

The accent is supplied by \`VantaClientPreferences\`. It must be the same accent for every screen and component. Status colors are semantic and must not be replaced by the accent. Derive alpha variants centrally rather than hand-picking shades per component.

## 3. Typography

Use Minecraft's existing font and text renderer. Do not add a font dependency for the first framework milestone.

| Role | Target size | Use |
|---|---:|---|
| Display | 18 px | Screen/page title |
| Section | 13 px | Section heading |
| Body | 10 px | Setting name and primary content |
| Supporting | 9 px | Description and secondary information |
| Label | 8 px | Compact metadata and key hints |
| Numeric | 9–10 px | FPS, percentages, slider values |

Typography is defined by role and color, not ad-hoc formatting. Important values should be aligned consistently; descriptions should not compete with labels.

## 4. Spacing and geometry

Base spacing unit: 4 GUI pixels.

- Spacing scale: 4, 8, 12, 16, 20, 24, 32.
- Default content inset: 20.
- Dense row vertical padding: 8.
- Standard row vertical padding: 10.
- Control heights: 22 compact, 26 standard, 30 prominent.
- Sidebar width: responsive within defined minimum/maximum bounds, not a fixed global coordinate.
- Default corner radii: 4 for compact controls, 6 for inputs, 8 for panels, 10 only for major window corners.
- Use one-pixel separators selectively. Do not outline every child.

## 5. Surface hierarchy

1. Canvas — the world/screen backdrop.
2. Main panel — one clear application surface.
3. Sidebar and content region — separate by tone and spacing, not stacks of borders.
4. Raised surfaces — menus, dialogs and floating popovers only.
5. Controls — generally flat; state and accent communicate interaction.

Shadows are reserved for floating surfaces. Do not fake depth with multiple nested rounded rectangles.

## 6. Interaction states

Every interactive control has coherent default, hover, pressed, focused, selected, disabled and (where relevant) invalid states.

- Hover: small surface/value change, not a glowing halo.
- Pressed: visible and immediate feedback.
- Focus: persistent keyboard-visible indicator using accent and contrast.
- Selected: structural indication plus accent; do not rely only on color.
- Disabled: reduced contrast but still readable.
- Invalid: danger token and explicit context, not color alone.

Controls must remain usable without animation.

## 7. Motion

Motion is functional feedback, not decoration.

- Hover/pressed transitions: approximately 90–130 ms.
- Selection and popup transitions: approximately 140–180 ms.
- Easing: ease-out for entry and return-to-rest; no overshoot by default.
- Animation uses elapsed time rather than a fixed per-render interpolation factor.
- Respect \`VantaClientPreferences.animations\`; disabled means immediate state changes.
- No continuous animation for static content.

## 8. Responsive behavior

Use GUI-scaled logical coordinates. Measure the available viewport and compute layout from it.

- Preserve a minimum usable content width; switch to a narrower sidebar or compact navigation when space is insufficient.
- Scroll content inside an explicit viewport with clipping and matching input bounds.
- Recompute layout on screen resize and GUI-scale change.
- Avoid a single 920 × 540 coordinate plane that only scales the entire interface uniformly.
- Truncate only where the full value remains available via tooltip or detail view.
- Long labels/descriptions must wrap or use a deliberate overflow treatment.

## 9. Component quality bar

The shared library should cover text, icons, buttons, toggles, sliders, dropdowns, search, tabs, tooltips, dialogs, notifications, progress indicators and color selection. Components use the same typography, geometry, tokens and interaction state contract.

A component is not complete merely because it draws. It must also have reliable bounds, input handling, focus behavior, disabled semantics, clipping behavior where relevant and a clear binding contract to existing settings.

## 10. Design validation

The spec is not visual sign-off. The prototype and each migrated screen must be captured in a real Minecraft client at multiple GUI scales and resolutions. Screenshots must be reviewed for hierarchy, alignment, density, clipping, consistency and product identity. Visual changes are accepted only after a before/after inspection.
