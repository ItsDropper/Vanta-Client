# OneClient / Polyfrost UI Research

**Research date:** 2026-11-02

## 1. Product and repository distinction

OneClient is presented as a Minecraft client/modpack whose in-game configuration experience is powered by OneConfig. Polyfrost's public GitHub organization also contains **OneLauncher**, a Rust monorepo for the launcher application, OneClient distribution and backend. The launcher repository is not itself the in-game GUI implementation.

Sources:
- https://polyfrost.org/projects/oneclient
- https://github.com/Polyfrost/OneLauncher
- https://modrinth.com/project/oneclient-modpack

## 2. OneConfig current architecture

The current OneConfig repository uses the `v1` branch and is substantially different from its older V0 documentation.

Its modules are separated by responsibility. The repository describes modules for commands, configuration, configuration implementation, events, HUD, mod toggles, UI and utilities. The UI module README describes its job as bridging PolyUI and Minecraft and providing notifications; it lists dependencies on events, utilities, PolyUI and LWJGL. It also describes a NanoVG-based renderer loaded by `UIManager`, with version-specific implementation code.

The current OneConfig build also contains a `poly-compose` module. Its Gradle definition exposes Compose Foundation, Material, UI and UI utility APIs, while keeping runtime/coroutine dependencies separately scoped. This is evidence that Compose integration exists in the current codebase; it does not by itself establish that the module is a standalone Minecraft UI host that can be adopted without OneConfig's runtime and integration layer.

Sources:
- https://github.com/Polyfrost/OneConfig/tree/v1/modules
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/ui/README.md
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/ui/build.gradle.kts
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/poly-compose/build.gradle.kts

## 3. Compose runtime packaging

OneConfig has a separate `compose-bundle` project. Its build includes Compose Foundation, Material, runtime, UI, navigation, lifecycle/viewmodel and Skiko AWT components, including OS-specific Windows, Linux and macOS native runtime artifacts. The build nests those jars and excludes duplicate Kotlin/runtime artifacts.

Its Fabric metadata identifies the library mod as `compose-multiplatform`, declares Apache-2.0, and requires Fabric Language Kotlin. The Modrinth library description explicitly says that this bundle supplies the Compose libraries but does not itself provide the Minecraft integration that plugs Compose into a game.

This is a meaningful deployment and maintenance cost, not a single lightweight Java dependency.

Sources:
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/compose-bundle/build.gradle.kts
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/compose-bundle/src/main/resources/fabric.mod.json
- https://modrinth.com/project/eHe8QhtD

## 4. Documentation version caveat

The older OneConfig V0 documentation describes NanoVG rendering through `NanoVGHelper` and explicitly discourages use of the old element package because it was proprietary to OneConfig's internals, not portable and pending rewrite. It must not be treated as documentation for the current V1 implementation.

Sources:
- https://docs.polyfrost.org/oneconfig/gui/render-manager
- https://docs.polyfrost.org/oneconfig/gui/elements

## 5. What actually transfers to Vanta

The useful lessons are architectural and product-level:

- Separate configuration data and APIs from the UI implementation.
- Keep reusable UI primitives and Minecraft-specific adapters in distinct layers.
- Treat HUD editing as a first-class UI surface, not a collection of unrelated overlays.
- Centralize theme and component behavior.
- Design for search, navigation, profiles/settings discovery and consistent interaction.
- Invest in layout, typography, spacing and content hierarchy rather than relying on decoration.
- Keep version-specific Minecraft integration at a narrow boundary.

OneClient's quality cannot be attributed to Compose alone. Its presentation is also the result of design work, consistent component behavior, information architecture and a mature shared UI ecosystem.

## 6. License and dependency considerations

The OneConfig repository states LGPL-3.0 plus Additional Terms Applicable to OneConfig. The separately distributed Compose runtime bundle declares Apache-2.0. The OneConfig documentation repository declares GFDL 1.3 or later. These licenses apply to their respective projects; they are not interchangeable.

Adopting OneConfig code or modules would require reviewing the complete applicable license and additional terms, dependency graph, runtime distribution obligations and version compatibility. Vanta should not copy OneConfig source or assets as a shortcut.

Sources:
- https://github.com/Polyfrost/OneConfig/blob/v1/LICENSE
- https://github.com/Polyfrost/OneConfig/blob/v1/modules/compose-bundle/src/main/resources/fabric.mod.json
- https://github.com/Polyfrost/OneConfig-Documentation

## 7. Research limitations

The public source confirms the current module boundaries and dependency packaging described above. It does not provide enough evidence to assert that every current OneClient screen is implemented with one specific renderer or that PolyCompose is independently supported as a complete drop-in Minecraft UI framework. The implementation decision must therefore be based on Vanta's actual runtime constraints, not marketing claims or assumptions about OneClient internals.
