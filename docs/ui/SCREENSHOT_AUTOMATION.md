# Automated Vanta UI Screenshot Pipeline

## Purpose

The screenshot pipeline launches the actual Minecraft 1.21.11 client with Vanta Client loaded. It does not generate a web mockup or a synthetic rendering of the GUI.

It uses Fabric's client GameTest API and Loom's production client GameTest runner under Xvfb on GitHub Actions.

## How to run

The workflow is \`.github/workflows/ui-screenshots.yml\`.

It runs automatically when relevant UI, GameTest or Gradle files change. It can also be started manually from GitHub Actions using **Vanta UI screenshots → Run workflow**.

No local Minecraft installation, multiplayer server or manual mouse interaction is part of the capture process. The test does not create a world or connect to a server.

The Fabric test configuration deliberately has \`eula = false\`. This repository does not silently accept the Minecraft EULA. If the installed test runner requires acceptance even for screen-only client tests, that is a blocker which must be resolved transparently rather than changing this setting without authorization.

## Capture matrix

The \`VantaUiScreenshotGameTest\` test opens the real \`VantaUiPrototypeScreen\` and captures its three pages:

- Overview
- Visuals
- Controls

At each of these window/GUI-scale combinations:

| Window size | GUI scale |
|---|---:|
| 1280 × 720 | 1 |
| 1280 × 720 | 2 |
| 960 × 540 | 1 |
| 960 × 540 | 2 |

Expected output: 12 PNG files per successful run.

The test verifies each screenshot exists and is at least 1 KiB. This detects missing/empty captures, but it does not automatically prove that every component is visually correct.

## Artifacts

The workflow uploads:
- \`vanta-ui-screenshots\`: PNG screenshots, retained for 14 days.
- \`vanta-ui-test-diagnostics\`: client test logs/reports after failure, retained for 7 days.

The configured output directory is \`build/run/clientGameTest/screenshots/\`. If the selected Loom version emits screenshots elsewhere, update the upload path after inspecting the run output; do not claim captures succeeded solely because the artifact upload step ran.

## Current validation status

The main Gradle build passed after the GameTest source set and corrected Gradle configuration were added.

The first headless client screenshot workflow is currently the decisive integration check. Until it completes and its artifacts are inspected, do not claim that automatic screenshots have been successfully captured or visually reviewed.

## Limits

- This tests real Minecraft GUI rendering and Screen lifecycle, not GPU performance under a representative gameplay world.
- The current test captures static page states. It does not synthesize all mouse/keyboard interactions or automatically judge visual quality.
- Screenshot automation removes repeated manual screenshot capture. It does not eliminate all human acceptance testing, especially for subjective product quality and real input feel.
