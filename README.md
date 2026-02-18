# ModUIClient

Fabric client mod that renders [Nukkit ModUI](https://github.com/AigioL/NetEaseModNukkitUI) HUD and Stack UI on Minecraft Java Edition, bridged through [ViaBedrock](https://github.com/RaphiMC/ViaBedrock) / [ViaProxy](https://github.com/ViaVersion/ViaProxy).

## Overview

Nukkit servers (Bedrock Edition) use a custom UI framework called **ModUI** to display HUD overlays and modal screens. This mod implements a client-side renderer that interprets ModUI protocol messages (PY_RPC MsgPack) and renders them as native Java Edition UI elements.

```
Nukkit Server (Bedrock) ──PY_RPC──► ViaBedrock ──CustomPayload──► ModUIClient (this mod)
                                                                        │
                                                                   UIManager
                                                                   ├── HUD Tree (always visible overlay)
                                                                   └── Stack Tree (modal screen)
```

## Features

- **HUD Overlay**: Persistent UI elements rendered on top of the game (health bars, score displays, notifications)
- **Stack Screen**: Modal UI screens (menus, dialogs) that capture input
- **Layout Engine**: Bedrock-compatible layout system with anchor points, percentage sizes, and stack panels
- **Element Types**:
  - `image` / `imageElongate` / `imageTop` - Texture rendering with UV mapping, nine-slice, sprite sheet animation, rotation
  - `text` / `textLeft` / `textRight` - Multi-line text with font scaling, alignment, auto-wrapping, line padding
  - `button` / `buttonSlice` - Interactive buttons with hover/pressed states and nine-slice support
  - `panel` / `stackPanel` - Container elements with horizontal/vertical stacking
- **Size Expressions**: `100%` (parent), `100%c` (children sum), `100%cm` (max child), `100%sm` (max sibling), `50% + 10` (arithmetic)
- **Incremental Commands**: 25+ command types for runtime UI manipulation (SetText, SetVisible, AddElement, RemoveElement, etc.)
- **C2S Events**: Button click reporting, screen info synchronization

## Requirements

- Minecraft 1.21.11
- Fabric Loader >= 0.16.10
- Fabric API
- Java 21+
- [ViaProxy](https://github.com/ViaVersion/ViaProxy) with [ViaBedrock](https://github.com/RaphiMC/ViaBedrock) (for protocol bridging)

## Building

```bash
./gradlew build
```

Output jar: `build/libs/moduiclient-1.0.0.jar`

## Installation

Copy the built jar to your Fabric client's `mods/` directory alongside Fabric API.

## Protocol

### S2C Events (Server → Client)

| Event | Description |
|-------|-------------|
| `ResponseHudNodeDataEvent` | Initialize HUD element tree from JSON |
| `RequestCreateStackNodeEvent` | Open a modal Stack UI screen |
| `RequestControlNodeEvent` | Execute incremental UI commands |
| `RequestRemoveStackNodeEvent` | Close the Stack UI |

### C2S Events (Client → Server)

| Event | Description |
|-------|-------------|
| `RequestHudNodeDataEvent` | Request HUD data from server |
| `RequestClickHudBtEvent` | Report HUD button click |
| `RequestClickStackBtEvent` | Report Stack button click |
| `ScreenInfoEvent` | Send screen resolution info |

## License

[MIT](LICENSE)
