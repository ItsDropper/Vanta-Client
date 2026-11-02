# Automated Vanta UI Screenshot Pipeline

## Purpose

The workflow launches the actual Minecraft 1.21.11 client with Vanta Client loaded. It captures real GUI output through Fabric's client GameTest API under Xvfb on GitHub Actions; it does not render a mockup.

## Running it

Workflow: `.github/workflows/ui-screenshots.yml`

It runs on UI, HUD editor, resource, GameTest and Gradle changes, and can be started manually from GitHub Actions.

No local Minecraft installation, manual mouse interaction, multiplayer server or authenticated Minecraft account is required. The test does not create a world or connect to a server.

## Capture matrix

`VantaUiScreenshotGameTest` captures the live `VantaSettingsScreen` at seven states:

- Overview
- HUD category
- Visual category
- Combat category
- Misc category
- Appearance
- A built-in feature settings subpage

It also opens the real `HudEditorScreen`, selects a HUD element and captures its inspector panel.

All eight states are captured at four configurations:

| Window size | GUI scale |
|---|---:|
| 1280 × 720 | 1 |
| 1280 × 720 | 2 |
| 960 × 540 | 1 |
| 960 × 540 | 2 |

Expected output: 32 PNG files.

Each screenshot is checked for existence and a minimum size of 1 KiB. The workflow independently asserts that exactly 32 PNGs exist.

## Artifacts

- `vanta-ui-screenshots`: PNGs, retained for 14 days.
- `vanta-ui-test-diagnostics`: logs/reports on failure, retained for 7 days.

Fabric writes screenshots to `build/run/clientGameTest/screenshots/`; the workflow verifies and uploads that exact directory.

## Validation history

- Run [41537246163](https://github.com/ItsDropper/Vanta-Client/actions/runs/41537246163), commit `316b37e`: Minecraft client screenshot capture and the 16-file assertion passed.
- Run [41538706648](https://github.com/ItsDropper/Vanta-Client/actions/runs/41538706648), commit `601f804`: build and 16 screenshots passed. The screenshots were downloaded and reviewed; the compact HUD inspector was verified below the toolbar at GUI scales 1 and 2.
- The capture matrix has since been expanded to cover all four feature categories, Appearance, a feature subpage and the HUD editor. The 32-image workflow run is pending.

## Limits

- This validates real Minecraft GUI rendering and Screen lifecycle, not GPU performance in a representative gameplay world.
- Captures are static states. They do not exercise every mouse/keyboard interaction or automatically judge visual quality.
- Manual in-game interaction and final design acceptance remain separate checks.
