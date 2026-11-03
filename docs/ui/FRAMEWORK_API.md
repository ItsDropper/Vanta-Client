# Vanta UI Framework API

**Status:** initial retained UI API; subject to change while the prototype is being validated.

## Package

\`name.vantaclient.client.gui.framework\`

Core classes:
- \`VantaUiTheme\`: centralized visual and motion tokens.
- \`VantaUiNode\`: retained node tree and component behavior.
- \`VantaUiRuntime\`: per-screen layout, render, focus and input owner.

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

Call \`runtime.invalidateLayout()\` after a screen changes a node's visibility, child list or size-affecting state. The node tree does not currently subscribe to arbitrary observable state changes and cannot infer those mutations.

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
- There is no generic icon, keybind editor, dialog, notification, progress or color-picker component yet.
- The runtime is not a Compose replacement. It does not implement compiler transforms, recomposition effects, coroutine state or general-purpose modifier semantics.
- No automated layout/input unit test suite has been added; build success only confirms compilation.
