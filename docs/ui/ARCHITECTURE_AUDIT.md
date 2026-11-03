# Vanta Client UI Architecture Audit

**Audited branch:** \`main\`  
**Primary source audit:** [\`docs/architecture/2026-11-02-ui-framework-audit.md\`](../architecture/2026-11-02-ui-framework-audit.md)  
**Purpose of this document:** concise engineering baseline for the framework work.

## 1. Verified build baseline

| Item | Repository value |
|---|---|
| Minecraft | \`1.21.11\` |
| Fabric Loader | \`0.19.3\` |
| Fabric API | \`0.141.4+1.21.11\` |
| Fabric Loom | \`1.17-SNAPSHOT\` |
| Java source/target | Java 21 |
| Language | Java |
| Rendering baseline | Minecraft \`Screen\`, \`GuiGraphics\`, \`AbstractWidget\` |

The project uses official Mojang mappings and split main/client source sets. Client UI code is under \`src/client/java\`. The repository has a GitHub Actions build workflow that runs \`./gradlew build\` on pushes and pull requests with JDK 21.

## 2. Actual screen ownership

The GUI key opens `VantaIntegrationsScreen`, which owns the installed-mod library and selected-mod details. Its header links to `VantaSettingsScreen`, `VantaModrinthBrowserScreen` and `HudEditorScreen`. Built-in feature categories, feature settings and appearance preferences live in `VantaSettingsScreen`; third-party Mod Menu/config screens remain external handoffs.

`ClickGuiScreen.init()` redirects to the unified integrations screen and is a legacy compatibility path, not the main user-facing ClickGUI. The HUD editor remains a distinct interaction surface because its canvas selection and dragging model differs from ordinary settings widgets.
Other distinct screens include the Modrinth browser and \`HudEditorScreen\`. The HUD editor is a different interaction surface from the settings panel and should not be collapsed into ordinary settings widgets.

## 3. Current implementation

- `VantaSettingsScreen` uses `VantaUiNode`, `VantaUiRuntime` and `VantaUiTheme` for built-in HUD, Visual, Combat and Misc categories, feature settings and appearance preferences.
- `VantaIntegrationsScreen` uses the retained runtime for installed-mod inventory, favorites, details and configuration handoff. Its former manual-widget renderer and duplicated settings branches have been removed.
- `VantaModrinthBrowserScreen` also uses the retained runtime for discover/search, installed state, pagination and install actions; network work remains asynchronous.
- Local mod inventory refresh enumerates JAR metadata on a worker thread and does not invoke entrypoints. Experimental session loading is a separate, explicit per-mod action.
- The public addon API still exposes typed `SettingDefinition` values; `VantaIntegrationSettingsScreen` maps supported types into retained controls.
- Older `VantaPanelButton`, `VantaToggleSwitch` and `FeatureSliderWidget` implementations remain for legacy/compatibility surfaces, not as the rendering foundation for active settings and library routes.
- Legacy \`ClickGuiScreen\`, \`SettingsPanel\` and \`Setting\` implementations contain their own widget/render behavior and should not become the new framework template.
- \`HudRenderer\` delegates to \`HudManager\`; \`HudElement\` objects own in-game HUD positioning/rendering; \`HudEditorScreen\` and \`HudMouseHandler\` separately manage editor presentation and pointer state.

## 4. Appearance, scaling and state

\`VantaClientPreferences\` persists accent preset, animation preference, compact mode, descriptions, UI scale, panel opacity, HUD grid/snap, and favorite mods. This is a useful existing source of truth, but the old renderer has additional hardcoded surfaces and state colors. Accent selection alone is not a complete theme system.

The settings and library screens use `VantaUiRuntime` to map Minecraft GUI-scaled mouse coordinates into logical layout coordinates and apply one shared scale transform. The framework owns pointer hit-testing, focus, clipping, scrolling and floating dropdown overlays; screens should not layer a second widget-specific scaling conversion on top.

## 5. Highest-impact limitations

1. The retained framework still needs broader interaction tests beyond targeted smoke tests and screenshot coverage.
2. The Controls page edits the five registered Vanta mappings; a feature-level keybinding model is not defined. Generic dialog/progress components and comprehensive layout/input tests remain unfinished.
3. External mods that provide their own Mod Menu config screens remain outside Vanta's framework control.
4. The HUD canvas selection/drag overlay intentionally remains specialized rendering; only its toolbar and inspector use the shared component runtime.
5. Manual gameplay-session validation and performance profiling remain outstanding.

## 6. Existing functionality to preserve

- Feature enable/disable and configuration persistence.
- Feature-specific settings and existing getters/setters.
- Public addon API and typed integration settings.
- Mod Menu hand-off and Modrinth navigation.
- Appearance preferences and favorites.
- HUD element rendering, normalized positioning, scaling, dragging, selection and persistence.
- Minecraft keyboard, mouse, screen lifecycle and narration behavior.

## 7. Verification boundary

This is a source audit. The existence of the push-triggered workflow does not mean that its build has passed. No local Minecraft session, screenshot capture, GUI-scale test, allocation profile or gameplay performance measurement is claimed here. Those require an actual build/run and visual QA pass.

For deeper source findings, including the active OneClient/OneConfig architecture, see:
- [Repository audit and ecosystem research](../architecture/2026-11-02-ui-framework-audit.md)
- [OneClient / Polyfrost technical research](ONECLIENT_RESEARCH.md)
- [Architecture decision record](ADR-001-ui-foundation.md)
