# Automated Vanta UI Screenshot Pipeline

## Purpose

The workflow launches the actual Minecraft 1.21.11 client with Vanta Client loaded. It captures real GUI output through Fabric's client GameTest API under Xvfb on GitHub Actions; it does not render a mockup.

## Running it

Workflow: `.github/workflows/ui-screenshots.yml`

It runs on UI, HUD editor, resource, GameTest and Gradle changes, and can be started manually from GitHub Actions.

No local Minecraft installation, multiplayer server or authenticated Minecraft account is required. The test does not create a world or connect to a server.

## Capture matrix

`VantaUiScreenshotGameTest` captures nine states:

- Vanta title screen
- Settings overview
- HUD category
- Visual category
- Combat category
- Miscellaneous category
- Appearance
- A built-in feature settings subpage
- HUD editor with a selected element and inspector

All nine states are captured at four configurations:

| Window size | GUI scale |
|---|---:|
| 1280 × 720 | 1 |
| 1280 × 720 | 2 |
| 960 × 540 | 1 |
| 960 × 540 | 2 |

Expected output: **36 PNG files**. The workflow independently asserts that exactly 36 screenshots exist and each capture checks for a valid file of at least 1 KiB.

## Artifacts

- `vanta-ui-screenshots`: PNGs, retained for 14 days.
- `vanta-ui-test-diagnostics`: logs/reports on failure, retained for 7 days.

Fabric writes screenshots to `build/run/clientGameTest/screenshots/`; the workflow verifies and uploads that exact directory.

## Validation history

- Run [41537246163](https://github.com/ItsDropper/Vanta-Client/actions/runs/41537246163), commit `316b37e`: the original Minecraft client screenshot capture and 16-file assertion passed.
- Run [41538706648](https://github.com/ItsDropper/Vanta-Client/actions/runs/41538706648), commit `601f804`: build and 16 screenshots passed. The screenshots were reviewed for the compact HUD inspector at GUI scales 1 and 2.
- Run [41716077221](https://github.com/ItsDropper/Vanta-Client/actions/runs/41716077221), commit `5beed53`: the current 36-image capture workflow passed.

## Limits

- This validates real Minecraft GUI rendering and Screen lifecycle, not GPU performance in a representative gameplay world.
- Captures are static states. They do not exercise every mouse/keyboard interaction or automatically judge visual quality.
- The screenshot workflow does not capture the full Modrinth browser or every installed-mod state.
- Manual gameplay interaction and final design acceptance remain separate checks.
