# Vanta Client

A Minecraft 1.21.11 Fabric client mod with built-in HUD, visual and gameplay features, a unified settings panel, and a public addon integration API.

## Features

## HUD System

DropperUtils includes a fully customizable HUD system:

* Custom HUD Editor
* Drag and drop HUD positioning
* Automatic HUD position saving
* UI scale compatible positioning
* Per-HUD customization panels
* Adjustable HUD scaling
* Modern HUD editor interface

### Current HUDs

* Totem Counter HUD
* Armor HUD
* Potion Effects HUD
* Debug HUD

## PvP Features

### Anchor Optimizer

* Faster anchor placement workflow
* Improved anchor timing support
* Explosion prediction
* Optimized fast anchor techniques

### Other PvP Improvements

* Low Screen Fire
* No Explosion Effects
* No Fire Overlay

## Visual Features

* Fullbright
* Zoom
* Customizable quality of life improvements
* Lightweight visual enhancements

## Vanta Client Settings

The in-game panel provides:
- Vanta Client appearance preferences and persistent UI customization
- Built-in feature categories for HUD, Visual, Combat, and Misc
- An installed-mod browser populated from Fabric Loader metadata
- Direct configuration controls for mods that register with the Vanta Client API
- Optional hand-off to Mod Menu for mods that provide their own config screens

Mod Menu is optional. Vanta Client exposes its settings through Mod Menu's public API when it is installed.

## Vanta Client Integration API

Third-party Fabric mods can register typed settings through the public API. See [docs/API.md](docs/API.md) for the API v1 contract and integration example.

## Installation

1. Install Fabric Loader
2. Install Fabric API
3. Download Vanta Client
4. Place the mod file into your `.minecraft/mods` folder

## Requirements

* Minecraft 1.21.11
* Fabric Loader
* Fabric API

## Development

Built with:

* Java
* Fabric API
* Minecraft Client API

## Contributing

Suggestions, bug reports, and contributions are welcome.

## License

See the repository license for details.
