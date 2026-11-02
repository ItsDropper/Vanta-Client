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

The active configuration entry point is \`VantaIntegrationsScreen\`, opened by the GUI key in the client event path. \`ClickGuiScreen.init()\` redirects to the unified screen; it is a legacy compatibility path, not the main user-facing ClickGUI.

Other distinct screens include the Modrinth browser and \`HudEditorScreen\`. The HUD editor is a different interaction surface from the settings panel and should not be collapsed into ordinary settings widgets.

## 3. Current implementation

- \`VantaIntegrationsScreen\` owns section navigation, built-in features, installed-mod discovery, integration settings, appearance preferences and library navigation. It draws its shell/content manually and registers Minecraft widgets for interactive controls.
- \`VantaPanelButton\`, \`VantaToggleSwitch\` and \`FeatureSliderWidget\` independently implement appearance and interaction behavior.
- The active built-in feature settings use \`Feature\`, \`FeatureRegistry\` and \`SettingRegistry\`; the public addon API exposes typed \`SettingDefinition\` values and must remain compatible.
- Legacy \`ClickGuiScreen\`, \`SettingsPanel\` and \`Setting\` implementations contain their own widget/render behavior and should not become the new framework template.
- \`HudRenderer\` delegates to \`HudManager\`; \`HudElement\` objects own in-game HUD positioning/rendering; \`HudEditorScreen\` and \`HudMouseHandler\` separately manage editor presentation and pointer state.

## 4. Appearance, scaling and state

\`VantaClientPreferences\` persists accent preset, animation preference, compact mode, descriptions, UI scale, panel opacity, HUD grid/snap, and favorite mods. This is a useful existing source of truth, but the old renderer has additional hardcoded surfaces and state colors. Accent selection alone is not a complete theme system.

The main settings panel uses a 920×540 logical design and coordinate conversion helpers in \`VantaPanelButton\`. The new framework must own one consistent mapping between Minecraft GUI-scaled mouse coordinates and layout coordinates. Do not apply independent widget and pose-stack scaling twice.

## 5. Highest-impact limitations

1. No shared measure/arrange layout engine for Row, Column, Box/Overlay and constraints.
2. UI controls duplicate rendering, hover/focus and animation logic.
3. No common input/focus/scroll/clip contract.
4. Some built-in setting definitions are coupled to legacy ClickGUI widgets.
5. HUD editor and settings screen have separate interaction/state approaches.
6. Theme data exists but is not consumed by every legacy component.
7. No in-game visual baseline has been captured by this repository audit.

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
