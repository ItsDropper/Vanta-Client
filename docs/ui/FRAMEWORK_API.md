# Vanta UI Framework API

**Status:** retained UI API with shared component factories; screen-specific APIs remain subject to change.

## Package

\`name.vantaclient.client.gui.framework\`

Core classes:
- \`VantaUiTheme\`: centralized visual and motion tokens.
- \`VantaUiNode\`: retained node tree and component behavior.
- \`VantaUiRuntime\`: per-screen layout, render, focus and input owner.

## Visual design tokens

The visual system is resolved as an immutable `VantaTheme` snapshot by
`VantaThemeFactory.create()`. The snapshot groups:

- `Palette`: canvas and text hierarchy, user accent and its computed foreground, borders,
  focus/selection, and info/success/warning/danger semantic colors.
- `Spacing`: ordered 4–24 px spacing scale, control/row dimensions and the small-to-panel
  corner-radius scale.
- `Typography`: display, heading, title, body, label and caption sizes with independent
  line heights.
- `Glass`: panel/elevated/card/field/popover tints and opacity plus shadow, highlight,
  border, hover, selection, focus and disabled-state alpha.
- `Motion`: hover/press/selection rates and the existing reduced-motion preference.

Use `VantaTypography.Role` rather than choosing a font resource ad hoc. The roles map to
the bundled `vanta:brand`, `vanta:heading`, `vanta:title`, `vanta:ui`,
`vanta:medium` and `vanta:caption` providers. `VantaUiNode.display()`,
`heading()`, `title()`, `label()` and `eyebrow()` preserve that hierarchy and use matching
line-height tokens.

Primary action buttons compute a monochrome foreground from the active accent's relative
luminance. The four shipped accent presets are regression-tested to meet WCAG AA's 4.5:1
normal-text contrast target. Add new accent presets only with a passing contrast test.

The Minecraft `GuiGraphics` backend remains the renderer. The design system defines
appearance and state; it does not require a second graphics API or a shader dependency.

## Construction

Create a new tree when a screen is initialized. Nodes are retained and should not be reconstructed every frame.

\`\`\`java
VantaUiNode root = VantaUiNode.column(12, 16)
        .background(VantaUiTheme.CANVAS);

VantaUiNode header = VantaUiNode.row(8, 8)
        .height(44)
        .background(VantaUiTheme.PANEL);

header.add(VantaUiNode.text("VANTA", VantaUiTheme.TEXT_PRIMARY));
header.add(VantaUiNode.spacer(1).flex(1.0f));
header.add(VantaUiNode.button("Close", this::onClose));

root.add(header);

VantaUiNode content = VantaUiNode.column(8, 12)
        .flex(1.0f)
        .scrollable();

content.add(VantaUiNode.toggle(
        "Show FPS",
        this::isFpsEnabled,
        this::setFpsEnabled
));

content.add(VantaUiNode.slider(
        "Render distance",
        2.0,
        32.0,
        1.0,
        this::getRenderDistance,
        this::setRenderDistance
));

content.add(VantaUiNode.dropdown(
        "Mode",
        List.of("Balanced", "Low latency", "Power saver"),
        this::getMode,
        this::setMode
));

root.add(content);
runtime = new VantaUiRuntime(root);
runtime.layout(font, width, height);
\`\`\`

The example shows the current component contract. The supplier/setter methods are owned by the screen or by a settings adapter; the UI runtime does not own configuration persistence.

## Layout containers

| Factory | Direction | Intended use |
|---|---|---|
| \`row(gap, padding)\` | Horizontal | Toolbar, metrics, inline controls |
| \`column(gap, padding)\` | Vertical | Sections, sidebars and scrollable content |
| \`stack(padding)\` | Overlay | Layered content and aligned overlays |
| \`box(padding)\` | Stack alias | General container |
| \`overlay(padding)\` | Stack alias | Explicit overlay container |

Child modifiers:
- \`width\`, \`height\`, \`size\`
- \`minSize\`, \`maxSize\`
- \`flex\`
- \`align(START | CENTER | END | STRETCH)\`
- \`justify(START | CENTER | END | SPACE_BETWEEN)\`
- \`padding\`, \`gap\`
- \`background\`, \`clipToBounds\`, \`scrollable\`

All dimensions and pointer positions use Minecraft GUI-scaled logical coordinates. Individual nodes must not perform framebuffer-scale multiplication or coordinate conversion.

## Controls and binding rules

- Text and paragraphs are presentation-only.
- Buttons invoke an action; they do not persist state.
- Toggles read a boolean supplier and invoke a boolean setter.
- Sliders use a finite ordered range, optional positive step and numeric getter/setter.
- Dropdowns copy a finite option list and use a selected-value getter/setter.
- Text fields use a string getter/setter and maximum UTF-16 length.
- Indeterminate progress indicators render a concise status label and an animated accent segment. They do not invent percentages for operations that do not report transfer progress.

Callbacks should update the existing Vanta feature/configuration objects. The framework must not introduce a second configuration store or save preferences directly.

Every live component binding is read when rendered or interacted with. Keep suppliers cheap and side-effect free. Setters should perform the domain operation and persistence required by the existing model.

## Screen lifecycle

A Minecraft \`Screen\` owns exactly one \`VantaUiRuntime\` instance.

1. Construct the root tree in \`init()\`.
2. Create the runtime and call \`layout(font, width, height)\`.
3. In \`render(...)\`, call \`runtime.render(graphics, font, width, height, mouseX, mouseY)\`.
4. Forward mouse click, drag, release, scroll, key and character events to the matching runtime methods before falling back to \`super\`.
5. Call \`runtime.tick()\` from screen tick if tick-driven controls (such as the text caret) are used.
6. Call \`runtime.dispose()\` when the screen is removed.

The runtime owns focus, slider pointer capture, hover, dropdown-open state and frame animation timing. Do not store these as global static fields.

## Layout invalidation

The runtime automatically remeasures when screen dimensions change. It also invalidates for internal interactions that change component geometry, such as scrolling. Opening a dropdown does not change the control's measured height or reflow its parent.

Call `runtime.invalidateLayout()` after a screen changes a node's visibility, child list or size-affecting state. Components created through the state-bound `VantaComponents` factories register their `VantaState` bindings with the runtime; paint-only writes invalidate rendering, while explicitly layout-affecting bindings request remeasurement. Arbitrary external mutations still require explicit invalidation.

If the screen replaces the entire composition, dispose the old runtime and create a new one.

## Input and focus

- Pointer coordinates are already in Minecraft's GUI-scaled coordinate space.
- The runtime routes input through the same node tree used for rendering.
- Parent clip bounds constrain normal child rendering and hit testing.
- Open dropdown menus render in a separate overlay pass above parent scissor regions. The popup stays within the logical viewport, shows at most eight options at once, and supports independent wheel scrolling without resizing the source control.
- Tab / Shift+Tab traverse interactive nodes in tree order.
- Enter / Space activates the focused button/toggle or confirms an open dropdown.
- Arrow keys adjust focused sliders and dropdowns.
- Escape closes a focused open dropdown; otherwise the Minecraft screen receives Escape.
- The text field supports character insertion, pointer caret positioning, backspace/delete and left/right/home/end.

## Performance and measurement

The runtime retains nodes and only recomputes layout after size changes or explicit invalidation. Focus traversal allocates a temporary list when Tab is pressed; layout allocates measurement/placement data only during layout passes.

The prototype exposes a smoothed CPU-side command-submission timer. It measures Java-side UI draw command generation and tooltip rendering only. It does not measure GPU execution, synchronization or total Minecraft frame time. Actual profiling remains a manual runtime requirement.

## Current limitations

- Scroll containers support wheel scrolling, clipped pointer input and draggable scrollbar thumbs. The screenshot GameTest includes a targeted thumb-drag smoke assertion, but this is not a comprehensive input-test suite.
- Dropdowns render bounded floating popups outside parent clipping, do not reflow their containing layout, and support wheel scrolling, mouse selection and keyboard navigation. Lists are windowed to at most eight visible options; this is not a virtualized data model.
- Generic modal dialog and color-picker components are not implemented. Progress is currently indeterminate; a determinate transfer bar requires a downloader API that exposes byte progress.
- The runtime is not a Compose replacement. It does not implement compiler transforms, recomposition effects, coroutine state or general-purpose modifier semantics.
- No automated layout/input unit test suite has been added; build success only confirms compilation.


## Shared component library

Package: `name.vantaclient.client.gui.compose.components.VantaComponents`

Use the shared factories rather than duplicating common node compositions in each screen:

- `panel(Surface, gap, padding)`: bordered, rounded panel using the shared panel/elevated/field/popover materials.
- `divider()`: one-pixel layout separator.
- `badge(label, Tone)`: compact neutral, accent, success, warning or danger status.
- `actionButton(label, icon, ButtonVariant, action)`: primary, secondary, quiet or danger action.
- `sectionHeader(eyebrow, title, detail)`: consistent section hierarchy.
- `settingRow(title, description, control, controlWidth)`: label/description plus a caller-owned control.
- `emptyState(icon, title, description, actionLabel, action)`: empty collection or unavailable-content state.
- `statusRow(title, detail, status, Tone)`: information row with a semantic status badge.
- `moduleCard(...)` and `workspaceRow(...)`: reusable domain-neutral library/workspace compositions.

`VantaCompose` exposes stable delegation methods for the shared factories. The component layer only composes retained nodes: it does not own feature state, persistence, network work, screen navigation or modal lifecycle. Use `VantaState` overloads for toggles, sliders, text fields and dropdowns when multiple controls or screen logic share a value. The owning `VantaUiRuntime` manages their subscriptions and disposes them with the screen.

Example:

```java
VantaUiNode content = VantaComponents.panel(
        VantaComponents.Surface.PANEL, 8, 12
);
content.add(VantaComponents.sectionHeader(
        "DISPLAY", "Visual settings", "Configure the client presentation."
));
content.add(VantaComponents.statusRow(
        "World connection", "Direct peer connection", "Connected",
        VantaComponents.Tone.SUCCESS
));
content.add(VantaComponents.settingRow(
        "Show FPS", "Display the current frame rate.",
        VantaComponents.toggle("Show FPS", showFpsState), 0
));
```

All components expose the same `VantaUiComponent` semantic tree as their underlying nodes. Composite components preserve their visible text and interactive descendants for future accessibility adapters.

## Canvas nodes and the menu skin

`VantaUiNode.canvas(painter)` creates a node painted by caller code. The painter receives the node
bounds and a `PaintState` (`hovered`, `focused`, eased `hover` 0..1, local mouse position). Add
`onClick` or `onClickAt` to make it interactive. `radius`, `border` and `font` adjust a node's own
background, outline and label font. `menuStyle()` switches toggles, sliders, dropdowns and text
fields to the flat menu skin used by `gui/menu`.

`VantaUiRuntime.setZoom(float)` multiplies the preferred scale so a full-window menu can size
itself to the window. `VantaSettingsScreen` picks an eighth-step zoom that fits its 1400x780 design
size to about 94% x 90% of the window.

`VantaUiTheme.fillSquircle`/`outlineSquircle` anti-alias their corner arcs with one partial-alpha
pixel per row. `fillSquircleGradient` and `fillSquircleSides` draw vertical gradients and
half-rounded shapes.

Text metrics: Minecraft draws TTF text with its baseline 7 units below the draw position, and Poppins
capitals are about 0.7 em tall, so `MenuTheme.textTop(centerY, size)` is
`centerY - 7 + round(0.35 * size)`.
