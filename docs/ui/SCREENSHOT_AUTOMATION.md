# Automated Vanta UI Screenshot Pipeline

## Purpose

The workflow launches the actual Minecraft 1.21.11 client with Vanta Client loaded. It captures the real GUI through Fabric's client GameTest API under Xvfb on GitHub Actions; it does not render a mockup.

## Running it

Workflow: `.github/workflows/ui-screenshots.yml`

It runs on relevant UI/GameTest/Gradle changes and can also be started manually from GitHub Actions using **Vanta UI screenshots → Run workflow**.

No local Minecraft installation, manual mouse interaction, multiplayer server, or authenticated Minecraft account is required. The test does not create a world or connect to a server.

## Capture matrix

The `VantaUiScreenshotGameTest` opens the real `VantaUiPrototypeScreen` and captures three pages:

- Overview
- Visuals
- Controls

Each page is captured at four configurations:

| Window size | GUI scale |
|---|---:|
| 1280 × 720 | 1 |
| 1280 × 720 | 2 |
| 960 × 540 | 1 |
| 960 × 540 | 2 |

Expected output: 12 PNG files.

Each screenshot is checked for existence and a minimum size of 1 KiB. The workflow independently asserts that exactly 12 PNGs exist.

## Artifacts

- `vanta-ui-screenshots`: PNGs, retained for 14 days.
- `vanta-ui-test-diagnostics`: logs/reports on failure, retained for 7 days.

Fabric writes screenshots to `build/run/clientGameTest/screenshots/`. The workflow verifies and uploads that exact directory.

## Current validation status

The first correctly discovered client GameTest run successfully launched Minecraft and logged all 12 expected captures. The workflow then failed because its verification/upload steps looked in a different directory. The path mismatch has been corrected; a subsequent successful artifact upload and inspection is still required before marking the pipeline fully validated.

## Limits

- This validates real Minecraft GUI rendering and Screen lifecycle, not GPU performance in a representative gameplay world.
- Captures are static page states. They do not exercise every mouse/keyboard interaction or automatically judge visual quality.
- Automation removes repeated manual screenshot capture, but subjective design acceptance and real input feel still benefit from human review.
