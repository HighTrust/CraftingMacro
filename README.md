# Crafting Macro

A **client-side Fabric mod for Minecraft Java 26.3** that lets you bind keys to commands, automatic crafting and custom action sequences, all configured in-game without editing files.

- **Command macros:** run `/craft`, `/home`, `/spawn` and other commands, with delays between them.
- **Auto-crafting:** pick a recipe and a quantity. The mod checks your ingredients and inventory space, opens a crafting grid, crafts, collects the results and closes the screen.
- **Sequences:** chain commands, crafting, waits, slot clicks, item moves, notifications and repeats.
- **Click / Hold / Toggle:** Click runs once (e.g. 1 snow block). Hold crafts as much as possible until you let go. Toggle crafts as much as possible until you press again or run out.
- **Custom patterns:** design a 3×3 layout in a fake crafting table, and the mod places the items for you with normal clicks. Works with custom server recipes.
- **Works inside an open crafting table:** press your macro key while the table is open and it fills the grid and crafts. No `/craft` needed.
- **World actions:** walk, look, left/right-click (hold or tap), hold a specific item, and mine chosen blocks nearby (walking to them). Includes a ready-made **Shovel Snow** toggle on N.
- **Animated "+N crafted" feed and a Stats screen** showing totals and the resources used.
- A dark in-game manager (**Right Shift**) with search, enable switches, keybind conflict warnings, profiles, import/export and notifications.

> Client-side only. No server plugin is needed, and the mod only does what you could do by hand. It uses the same recipe-book and click packets as vanilla, so every action is checked by the server. It contains no dupes, no anticheat bypasses and no permission bypasses.

## Requirements

| | Version |
|---|---|
| Minecraft | **26.3** |
| Fabric Loader | 0.18.0+ |
| Fabric API | 0.165.2+26.3 |
| Java | 25+ |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and `craftingmacro-<version>.jar` (from [Releases](https://github.com/HighTrust/CraftingMacro/releases) or built yourself) into `.minecraft/mods/`.
3. Launch the game and press **Right Shift** in-game.

## Default example macros

These are ordinary macros. You can edit, rebind or delete them.

| Key | Macro | What it does |
|---|---|---|
| **K** | Open Crafting Table | Runs `/craft` (on servers that provide it) |
| **L** | Craft Snow Block | 4 snowballs → 1 snow block, using the 2×2 inventory grid |
| **M** | Craft Snow Layers | 3 crafting operations (9 snow blocks → 18 layers), using a 3×3 table |

Vanilla uses **L** for Advancements. While "Macro keys override vanilla keys" is on (the default), only the macro runs.

## Controls

| Action | Default | Where to change it |
|---|---|---|
| Open Macro Manager | Right Shift | Options → Controls → Crafting Macro |
| Stop All Macros (emergency) | End | Options → Controls → Crafting Macro |
| Macro keys | per macro | In the macro editor |

See the **[User Guide](docs/USER_GUIDE.md)** for a full walkthrough.

## Building from source

```bash
git clone https://github.com/HighTrust/CraftingMacro.git
cd CraftingMacro
./gradlew build          # jar in build/libs/
./gradlew test           # unit tests
./gradlew runClient      # dev client
```

## Project layout

```
src/main/java/io/github/hightrust/craftingmacro/
├── CraftingMacroClient.java   entrypoint, tick + lifecycle wiring
├── MacroManager.java          config, executor, notifications
├── macro/      data model, validation, keybind conflicts   (pure Java)
├── exec/       tick-driven executor, run state machine, step handlers
├── crafting/   recipe resolver, ingredient planner, inventory analysis, crafting state machine
├── config/     JSON persistence, profiles, migration, validation, import/export
├── input/      key polling and vanilla key mappings
├── gui/        screens and widgets
└── notify/     HUD notifications
src/test/java/  69 JUnit tests for the pure logic
```

To add a new step type, add an entry to `StepType` and a `StepHandler` in `StepHandlers`. The executor, editor and config pick it up automatically.

## Testing status

- **Automated:** 69 JUnit tests pass. They cover config serialization, validation, keybind conflicts, sequence ordering, cooldowns, ingredient math, inventory capacity, cancellation and timeouts, migration, and import safety.
- **Live:** the mod compiles against 26.3, loads in the development client and reaches the title screen without errors.
- **Not yet verified live:** in-world crafting in singleplayer, and anything on a multiplayer server. Please report results in Issues.

## Known limitations

- **Recipes must be unlocked in your recipe book.** Since 1.21.2 the server only sends the client unlocked recipes, and it rejects recipe placement for locked ones. Craft the item by hand once, or pick up an ingredient to unlock it.
- 3×3 recipes need a crafting table under your crosshair or a server command that opens one (default `/craft`, configurable in Settings).
- Special recipes without fixed ingredients (dyeing, map cloning, etc.) cannot be auto-crafted.
- Macro keys are keyboard keys only.
- Server-side plugins that block automation or limit packets can still reject actions. The mod reports this and does not work around it.

## License

[MIT](LICENSE)
