# User Guide

## Opening the manager

Press **Right Shift** while in a world. To rebind it, go to Options → Controls → Key Binds → *Crafting Macro*. The game keeps running while the menu is open, so you can watch macros work.

The main screen has:

- **Search:** filters by name, description, key or step.
- **Macro list:** shows the key badge (orange means a conflict) and an on/off switch. Double-click a macro to edit it.
- **Details pane:** shows the selected macro's settings and steps. **Run now** tests the macro.
- **Bottom bar:** Create, Edit, Duplicate, Delete (asks first) and **Stop All**.
- **Profile** and **Settings** buttons in the header.

## Example: create a "Craft 16 snow blocks on F6" macro

1. **+ Create Macro**.
2. **General** tab: name it, click **Key** and press **F6**. Set **Type** to *Auto craft*.
3. **Steps** tab: click **Choose recipe...**, search "snow", select *Snow Block*, enter quantity **16** and click **Select**.
4. Optional: **+ Add Step** → *Notification* with "Snow ready!".
5. **Save**, close the menu and press **F6**.

The macro checks that you have 64 snowballs and room in your inventory, crafts 16 snow blocks (in the 2×2 grid, since the recipe fits), moves them to your inventory and reports how many it crafted.

To force the server's crafting table instead, use a *Sequence* with these steps: `Run command /craft` → `Open crafting` → `Craft recipe` → `Close screen` → `Notification`.

## Step types

| Step | What it does |
|---|---|
| Run command | Sends a command as you. Server permissions apply. |
| Open crafting | Uses the crafting table you are looking at, or the open command, then **waits until the server opens it**. |
| Craft recipe | Crafts *quantity* result items through the recipe book and collects them. |
| Wait (ticks / ms) | Pauses. 20 ticks = 1 second. |
| Click slot | Left/right/shift-clicks a slot index in the open screen. In a crafting table: 0 = result, 1–9 = grid, 10–45 = inventory. |
| Move items | Moves the stack from one slot to another. Leftovers go back to the source slot and nothing is dropped. |
| Close screen | Closes the open container. |
| Notification | Shows your message on screen. |
| Repeat previous | Repeats the previous N steps a number of extra times. |

Use **^ / v** to reorder steps, **Edit** to change one and **X** to remove it.

## Options tab

- **Cooldown / Start delay:** in milliseconds.
- **Repeat count:** how many times the whole step list runs per key press.
- **Hold to repeat:** keeps running while the key is held, every *Hold interval* ms.
- **Notifications:** success messages for this macro. Errors always show.
- **Auto-close crafting:** closes a crafting screen the macro opened. It stays open if items would otherwise be dropped.
- **Stop on error:** stops at the first failure. When off, the macro keeps going and reports warnings.
- **Servers:** limits the macro to specific server addresses. Empty means everywhere, and `singleplayer` means local worlds.

## Keybinds

- Any keyboard key, optionally with Ctrl, Shift and/or Alt.
- In the key recorder, **Esc** cancels and **Backspace** unbinds. Pressing and releasing a modifier on its own binds that modifier.
- Conflicts with other macros or vanilla keys are shown in the editor. If two enabled macros share a combo, only the first runs and you get a warning.
- Macros never fire while you are typing (chat, signs, search boxes). By default they only fire with no screen open; you can allow them in inventory screens in Settings.

## Profiles

In **Profile → Profiles** you can create, rename, duplicate, delete, activate and **link a profile to the current server** so it switches automatically when you join.

- **Export** writes `.minecraft/config/craftingmacro/exports/<name>.json`.
- **Import** lists files in that folder. Imported data is validated and clamped. It is plain data: it cannot run code or point to other files.
- **Reset all** restores the K / L / M defaults.

The configuration lives in `.minecraft/config/craftingmacro/macros.json` and is saved automatically. A corrupt file is backed up and replaced with defaults.

## Safety behaviour

- Only one inventory-changing macro runs at a time. Command-only macros can run alongside it.
- Every wait for the server has a timeout. If you raise **Server timeout** in Settings, the mod waits longer on laggy servers.
- Disconnecting, dying, changing dimension or closing the crafting screen stops the macro safely.
- Press **End** (Stop All) at any time.

## Troubleshooting

| Message | Fix |
|---|---|
| No unlocked crafting recipe | Unlock it in the recipe book (craft it by hand once). |
| Missing ingredients | The message lists what's missing. |
| Inventory full | Free some slots. Leftovers stay in the grid instead of being dropped. |
| Crafting interface did not open via /craft | The server lacks that command or you lack permission. Look at a crafting table, or change the command in Settings. |
| Command may be unavailable | The server didn't advertise the command. It is sent anyway. |
| Cooldown active | Wait, or lower the macro's cooldown. |
