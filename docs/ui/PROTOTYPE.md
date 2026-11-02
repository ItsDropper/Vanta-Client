# Vanta UI Framework Prototype

## Entry point

Open Vanta Client's **Settings** section, then select **Open UI Prototype** under Appearance.

The prototype is a separate Minecraft \`Screen\`. It does not replace the active settings screen.

## What it demonstrates

- Native Minecraft rendering through \`GuiGraphics\`.
- Retained component tree with Row, Column and Stack containers.
- Padding, gap, preferred/min/max sizes, flex, alignment and justification.
- Scrollable content with clipping and matching pointer bounds.
- Text, bounded paragraph, button, toggle, slider, expanded dropdown selector and text field.
- Mouse click, slider drag/capture/release and wheel input.
- Keyboard Tab/Shift+Tab focus traversal, Enter/Space activation, directional adjustment, Escape dismissal and text editing.
- Tooltips and focus feedback.
- Live FPS readout.
- Appearance accent selection and global animation preference.

## State ownership

The live FPS readout reads Minecraft's FPS metric. The accent control updates the existing \`VantaClientPreferences.accent\` value and persists it. The reduced-animation control updates the existing Appearance \`animations\` preference.

Render distance, crosshair style/size, HUD grid/scale, keybind mode and search text are isolated local demonstration state. They do not modify live video settings, feature configuration or HUD layout.

This is intentional. The prototype must validate the UI runtime independently before real game settings are connected through adapters.

## Manual in-game test matrix

No in-game test is marked as passed by this repository change. Run these checks in the actual target Minecraft/Fabric profile:

| Test | Procedure | Expected |
|---|---|---|
| Open/close | Open prototype from Appearance, close with Escape, reopen | Returns to parent screen; no stale UI state |
| Navigation | Switch Overview / Visuals / Controls | Correct page, previous page state retained where expected |
| Toggle | Click a toggle and use keyboard activation | Value changes once and feedback follows state |
| Slider | Click, drag outside bounds, release, use arrows | Value clamps to min/max and drag capture ends on release |
| Dropdown | Open, select an option, use arrows/Enter and Escape | Expanded option list selects a value; keyboard highlight commits; Escape closes without changing the value |
| Text field | Focus, type, move caret, backspace/delete, Tab away | Edits only prototype value; text remains bounded |
| Scroll | Wheel over content, wheel over navigation/background | Only the hovered scroll viewport consumes wheel |
| Clip | Scroll content until rows and dropdown options pass viewport edges | Offscreen content is neither drawn nor interactive; all options remain reachable through the content scroll viewport |
| Tooltip | Hover each control near window edges | Tooltip stays within window bounds |
| Accent | Change accent in prototype | Accent/selection/focus controls update consistently |
| Animation | Toggle global animations | Motion snaps when disabled |
| GUI scale | Test all practical GUI scale values | Pointer and visuals stay aligned |
| Resolution | Resize window and change aspect ratio | Layout remeasures without overlap or stale hitboxes |
| Lifecycle | Close while dragging slider, reopen | No stuck pointer capture or stale focus |

## Verification status

- Source implementation: committed.
- GitHub Actions builds passed for multiple intermediate framework commits; latest integrated commit is being validated separately.
- In-game input/render/scale checks: pending local Minecraft execution.
- Screenshot review: pending actual in-game screenshots.
- Rendering overhead measurement: not yet measured.

A successful build alone is not a visual sign-off.
