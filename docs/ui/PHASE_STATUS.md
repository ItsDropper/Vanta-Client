# Vanta UI Framework — Phase Status

**Updated:** 2026-11-03  
**Branch:** `main`

This status distinguishes implemented code, successful CI, screenshot inspection and manual gameplay validation.

## Foundation

<badge>Implemented</badge>

- Retained `VantaUiNode` tree, `VantaUiRuntime` layout/input/focus/scroll handling and centralized `VantaUiTheme` tokens.
- Shared controls include buttons, toggles, sliders, dropdowns, text fields, text, tooltips and vector-style icons.
- IBM Plex Sans is already bundled and wired through Minecraft's font-provider resources, with its OFL license.
- UI scale and panel opacity are resolved through shared framework behavior rather than screen-specific duplicate transforms/colors.

## Production migration

<badge>Implemented; visual QA ongoing</badge>

- `VantaSettingsScreen` is now the primary client settings screen, opened by the GUI key and Mod Menu entry.
- Built-in HUD, Visual, Combat and Misc feature categories use the shared framework and retain their existing feature/settings getters, setters and config persistence.
- Appearance preferences are bound to the existing persisted preference model: accent, animations, compact layout, descriptions, UI scale and panel opacity.
- API-contributed mod settings use `VantaIntegrationSettingsScreen`, mapping BOOLEAN, INTEGER, DECIMAL, STRING and ENUM definitions to live framework controls.
- The old `VantaIntegrationsScreen` is retained as the separate mod-library/browser surface. Its legacy built-in settings entry is no longer the primary settings route.
- The HUD editor toolbar and selected-element inspector use the same `VantaUiRuntime`, theme and control components. HUD canvas hitboxes/selection outlines remain custom canvas rendering because they represent draggable game elements, not settings widgets.

## Visual verification

<badge>Real screenshots captured and inspected</badge>

The GitHub Actions screenshot workflow launches Minecraft 1.21.11 and captures 16 images: three live settings states plus the HUD editor/inspector at four window/GUI-scale combinations. The 16-image capture and count assertion passed on commit `2f71bb6`.

Visual review found a toolbar/inspector overlap at compact GUI scales. The inspector was moved below the toolbar, then the toolbar was anchored to the top edge. A newer screenshot run is required to verify that final alignment.

## Remaining work / acceptance gates

- Confirm the latest build and screenshot workflow after the final HUD toolbar alignment and subsequent settings-search changes.
- Review the latest 16-image artifact, especially the HUD editor at 960×540 GUI scale 2.
- Manually exercise search, feature toggles, feature sliders, addon enum/string controls, dropdown keyboard navigation, screen open/close and HUD drag/selection in a real game session.
- Confirm behavior with the user's preferred GUI scale and actual HUD element configuration.
- The separate Modrinth browser and mod-library shell remain independent screens; they are not part of the settings-page tree.

A successful compile or screenshot capture is not by itself proof of full functional acceptance.
