# Automated Vanta UI Screenshot Pipeline

## Purpose

The workflow launches the actual Minecraft 1.21.11 client with Vanta Client loaded. It captures real GUI output through Fabric's client GameTest API under Xvfb on GitHub Actions; it does not render a mockup.

## Running it

Workflow: `.github/workflows/ui-screenshots.yml`

It runs on settings UI, HUD editor, HUD element/renderer, resource, GameTest and Gradle changes, and can be started manually from GitHub Actions.

No local Minecraft installation, multiplayer server or authenticated Minecraft account is required. The test does not create a world or connect to a server.

## Capture matrix

`VantaUiScreenshotGameTest` captures ten states:

- Vanta title screen
- Settings overview
- HUD category
- Visual category
- Combat category
- Miscellaneous category
- Appearance
- Controls / key bindings
- A built-in feature settings subpage
- HUD editor with a selected element and inspector

All ten states are captured at six configurations:

| Window size | GUI scale | Effective GUI viewport |
|---|---:|---:|
| 1280 × 720 | 1 | 1280 × 720 |
| 1280 × 720 | 2 | 640 × 360 |
| 960 × 540 | 1 | 960 × 540 |
| 960 × 540 | 2 | 480 × 270 |
| 1920 × 1080 | 3 | 640 × 360 |
| 1920 × 1080 | 4 | 480 × 270 |

Expected output: **83 PNG files**. The workflow asserts that exactly 83 screenshots exist and each capture checks for a valid file of at least 1 KiB.

The 60-image matrix covers the title screen, eight settings pages (including Controls) and the HUD editor at six viewport/GUI-scale combinations, including true GUI scales 3 and 4. Two additional screenshots capture the Bondies Utils EZ integration list and its 12-setting detail page inside the production `VantaSettingsScreen` main-menu shell. The fixture mirrors the private Bondies repository's `BondiesVantaIntegration` descriptor so public Vanta CI does not require private-repository credentials; its values are inert. Both screenshots therefore exercise the same navigation, shell, typography, cards and controls used by the rest of the current main UI. Four viewport configurations exercise the retained-mode Modrinth browser (eight screenshots including its progress state), the installed-mod library and selected-mod details (eight screenshots), and world sharing (four screenshots). All additional screens use fixed local preview data and do not depend on the Modrinth API, CDN or a populated local mods folder.

## Interaction smoke check

Before capturing the screenshot matrix, the client GameTest:
- Creates a scrollable retained-mode panel, drags its scrollbar thumb from top to bottom, and asserts that the content position changes.
- Opens a 12-option dropdown near the bottom of a clipped scroll container, verifies that its floating popup stays within the viewport without reflowing the parent, scrolls popup options, selects by mouse and keyboard, and verifies that scrolling the parent dismisses the popup.
- Verifies that overlapping HUD backgrounds are always unioned (preserving the 80% opacity), adjacent rectangles merge transitively at the configured distance, and a smaller distance keeps separated elements apart.
- Opens the HUD editor with FPS and Keystrokes backgrounds enabled, places their hitboxes 2 px apart, confirms they merge into one background, and restores their original position and background preferences after capture.
- Verifies the armor HUD's hitbox includes its horizontal durability label and accurately covers the vertical slot stack.
- Opens the retained-mode Modrinth browser with a fixed six-project preview dataset and captures both desktop and compact layouts without making network requests.
- Opens the retained-mode installed-mod library with a fixed six-mod preview dataset and captures both desktop and compact layouts without reading a real mods folder.
- Captures the Controls page showing the five registered Vanta key mappings. Key capture supports keyboard and mouse input, Escape cancel, Backspace unbind and reset-to-default.
- Temporarily duplicates a Vanta binding and verifies the conflict warning, rebinds the GUI shortcut, confirms suppression lasts until key release, checks Escape cancel and Backspace unbind, then restores the original mapping.
- Opens a selected mod's details/configuration-routing view with the same local fixture and captures it at desktop and compact sizes.

These are targeted smoke checks in a real Minecraft client, not exhaustive coverage of every control or gameplay state. The Bondies Utils EZ screenshots validate the integrated main-menu presentation and API registration using a deterministic descriptor fixture; they do not launch the private Bondies mod or test its live configuration callbacks.

## Artifacts

- `vanta-ui-screenshots`: PNGs, retained for 14 days.
- `vanta-ui-test-diagnostics`: logs/reports on failure, retained for 7 days.

Fabric writes screenshots to `build/run/clientGameTest/screenshots/`; the workflow verifies and uploads that exact directory.

## Validation history

- Run [41537246163](https://github.com/ItsDropper/Vanta-Client/actions/runs/41537246163), commit `316b37e`: the original Minecraft client screenshot capture and 16-file assertion passed.
- Run [41538706648](https://github.com/ItsDropper/Vanta-Client/actions/runs/41538706648), commit `601f804`: build and 16 screenshots passed. The screenshots were reviewed for the compact HUD inspector at GUI scales 1 and 2.
- Run [41716077221](https://github.com/ItsDropper/Vanta-Client/actions/runs/41716077221), commit `5beed53`: the 36-image capture workflow passed.
- Run [41720774378](https://github.com/ItsDropper/Vanta-Client/actions/runs/41720774378), commit `a25a09a`: build and the 36-image capture passed.
- Run [41721290497](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721290497), commit `6902953`: build and the 36-image capture passed.
- Run [41721788364](https://github.com/ItsDropper/Vanta-Client/actions/runs/41721788364), commit `b23c391`: build and the 36-image capture passed, including the scrollbar-thumb drag assertion.
- Run [41723780313](https://github.com/ItsDropper/Vanta-Client/actions/runs/41723780313), commit `51ea19e`: build and 36 screenshots passed with the first connected-background regression check.
- Run [41729498054](https://github.com/ItsDropper/Vanta-Client/actions/runs/41729498054), commit `f58bf71`: build and 40 screenshots passed; the HUD editor screenshot includes two connected panels.
- Run [41729746874](https://github.com/ItsDropper/Vanta-Client/actions/runs/41729746874), commit `5209a47`: build and all 42 screenshots passed, including the installed-mod details route.
- Run [41735197828](https://github.com/ItsDropper/Vanta-Client/actions/runs/41735197828) and [41735197888](https://github.com/ItsDropper/Vanta-Client/actions/runs/41735197888), commit `f46f01c`: build and the 46-image matrix with the Controls page passed.
- Run [41735937594](https://github.com/ItsDropper/Vanta-Client/actions/runs/41735937594) and [41735937566](https://github.com/ItsDropper/Vanta-Client/actions/runs/41735937566), commit `1b95d17`: build and 46 images passed, including key rebinding, Escape cancel and Backspace unbind lifecycle tests.
- Run [41736445767](https://github.com/ItsDropper/Vanta-Client/actions/runs/41736445767) and [41736445960](https://github.com/ItsDropper/Vanta-Client/actions/runs/41736445960), commit `64fa3c9`: build and 46 images passed, including duplicate-binding conflict detection.

## Limits

- This validates real Minecraft GUI rendering and Screen lifecycle, not GPU performance in a representative gameplay world.
- Captures are static states. They do not exercise every mouse/keyboard interaction or automatically judge visual quality.
- Modrinth and installed-mod screens are captured with deterministic preview fixtures. The suite does not cover every network result, JAR state or third-party configuration screen.
- Manual gameplay interaction and final design acceptance remain separate checks.

The Bondies Utils EZ coverage also registers preview FPS, coordinates, ping, keystrokes and armor HUDs through the public `VantaHudElement` API, verifies that the HUD editor discovers all five and invokes the FPS position/scale persistence callbacks, and captures them in the real Vanta HUD editor.
