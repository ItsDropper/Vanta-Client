# VantaLoader client implementation

This directory is the independent native VantaLoader distribution of Vanta Client.
It is intentionally separate from the Fabric implementation under `src/`.

- Java sources are compiled from `vantaloader/src/main/java`.
- Native resources and the VMA descriptor are under `vantaloader/src/main/resources`.
- The artifact is built with `./gradlew vantaLoaderJar -Pwebrtc_platform=linux-x86_64`.
- Fabric Loader, Fabric API and Mod Menu are compile-time references only; they are
  not packaged into the native JAR. VantaLoader supplies the VMA API at runtime.
- Native lifecycle and HUD rendering are connected through VMA events. Fabric-only
  integrations remain confined to the Fabric distribution.

The dedicated GitHub Actions workflow builds this artifact, launches Minecraft with
the VantaLoader agent, and checks the client bootstrap log alongside real screenshots.
