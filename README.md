# Drop Rate Plugin
Instantly see the exact drop rate of items when monsters, bosses, or select minigames drop them, without leaving the game.

Created by **Pluckss**, with contributions from [@xTaig4](https://github.com/xTaig4).

## Behind the Scenes
This plugin started after I accidentally disassembled my Group Ironman's only Toxic blowpipe and had to grind out a replacement.

During that grind, I kept seeing dragonstone drops and repeatedly checking the rate in the wiki, even though I already knew it.

That frustration sparked the idea: show drop rates directly in chat so the information is always there when you need it.

## Features
- Shows a chat message when a dropped item has a known drop rate.
- Shows a hover tooltip in the Collection Log, and on the clue scroll reward screen, listing every source that drops an item with each rate.
- Prints a rate for clue casket rewards from Beginner to Master when you open a casket.
- Supports standard rates like `1/128` and bundle rates like `6/378` or `12/378`.
- Uses color-coded chat messages to quickly separate common, uncommon, and rare drops.
- Adds a purple ultra-rare tier for especially rare drops.
- Each tier color can be customized with a color picker.
- Colors and rarity checks are based on the effective chance of the drop, including bundle drops.
- Lets you keep the raw wiki rate, convert it to a standard `1/x`, or show both in chat.
- Can optionally append a rounded percentage such as `1/100 (1%)` or `4/51 (7.84%)`.
- Can optionally show source hints for ambiguous drops like `Normal 1/400 | RDT 1/5012.5`.
- Optional kill counter showing your KC and the average kills the drop takes.
- Optional notification when you receive a drop rarer than a threshold you choose.
- Optional minimum GE value filter to keep low-value drops out of chat.
- Lets you customize the color tier cutoffs or switch everything to neutral white text.
- Works with drop data whether it is stored as `NPC -> item` or `item -> NPC`.
- Supports both normal drop tables and the Rare Drop Table (RDT) with automatic dual lookup.
- Takes Ring of Wealth and your current Slayer task into account when resolving a rate.
- Matches the drop table to the NPC you actually killed, so an Abyssal demon in the Wilderness
  Slayer Cave, a level 106 Cyclops or a high-level Barbarian each report their own rate.
- Darkens the tier colors on the standard parchment chatbox so they stay readable.
- Supports minigame reward drops from Wintertodt, Tempoross, Guardians of the Rift, Soul Wars, and Barbarian Assault high gamble.
- Drop data is generated from the OSRS Wiki's own structured drop tables and checked against
  the wiki weekly, so rates track the game as it changes.
- Supports context-aware overrides for exceptions like task-based Hydra rates.
- Includes cleaner feed options so players can reduce chat clutter.

## Display
- `Drop visibility`
  Choose between `All drops`, `Notable drops only`, or `Rare drops only`.
- `Rate display`
  Defaults to `Standard (1/x)`, which converts rates like `2/222` into `1/111`. You can switch to `Raw wiki rate` to keep the wiki's own wording, or `Raw + standard` to show both.
- `Show percentage`
  Off by default. Adds a rounded percent chance to the end of the message.
- `Show source hints`
  Adds extra context for drops that can come from more than one table, such as both normal and RDT rates.
- `Show all table variants`
  On by default. When a monster has several drop tables and the kill cannot be matched to one
  of them, every table's rate is shown, labelled by version, for example
  `1x Adamantite bar (Standard 2/128 | Wilderness Slayer Cave 2/68)`. Turn it off to show only
  the first table's rate.
- `Rare-only minimum rate`
  Used only in `Rare drops only` mode. Example: `700` means show `1/700` and rarer.
- `Show kill counter`
  Off by default. Appends your kill count and the average kills the drop takes, for example
  `1x Abyssal whip (1/512 — KC: 203, avg: ~512 kills)`. A KC below the average means you got
  lucky. This is your real kill count, read from the same per-account record RuneLite builds
  from the in-game `Your ... kill count is:` message, so it survives restarts. Sources that
  never print that message have no real count to show, and fall back to a count for the
  current session only, labelled `KC: 8 this session`.
- `Kill counter min rarity`
  Only show the kill counter for drops rarer than this.

## Filtering
- `Show bundle drops`
  Includes common multi-roll or bundled drops such as `4/115`, `6/378`, or `12/378`.
- `Hidden filler items`
  Lets you hide custom filler items in `Notable drops only` mode with a comma-separated list.
- `Min item value (gp)`
  Hides drops whose whole stack is worth less than this on the GE. Set to `0` to disable.
  Untradeable drops such as pets are never hidden by this filter.

## Notifications
- `Notify on rare drops`
  Off by default. Uses RuneLite's standard notification settings, so you control tray popup,
  sound, screen flash, and focus behaviour. Useful when alt-tabbed.
- `Notification threshold`
  Minimum rarity to trigger a notification. Example: `1000` means only `1/1000` or rarer drops notify you.

## Appearance
- `Color style`
  Choose between `Tiered colors` and `Neutral white`.
- `Common tier max`
  Tiered colors only. Rates up to this value use the common color.
- `Rare tier minimum`
  Tiered colors only. Rates at or above this value use the rare color.
- `Ultra-rare tier minimum`
  Tiered colors only. Rates at or above this value use the ultra-rare color.
- `Common color` / `Uncommon color` / `Rare color` / `Ultra-rare color`
  Pick the color used for each tier.
- `Fit standard chatbox`
  On by default. The standard chatbox has a light parchment background that bright colors are
  hard to read on, so while it is active each color is darkened just enough to stay legible.
  The transparent chatbox always uses your colors exactly as picked.

## Tooltips
- `Collection log tooltips`
  On by default. Hovering an item in the Collection Log shows every source that drops it and
  its rate, with the most common source first. Sources that share a rate are grouped onto one line.
- `Clue reward tooltips`
  On by default. Hovering an item on the clue scroll reward screen shows the same tooltip.
- `Hide for obtained items`
  Off by default. Skips the tooltip for items you have already unlocked, in the Collection Log only.

Clue casket rewards from Beginner to Master also print in chat when you open a casket, and are included in the tooltip. Raid uniques and
skilling pets are deliberately shown without a rate, because no honest fixed number exists for
them — they depend on contribution, invocation level, or your skill level.

## Useful Notes
- The plugin reads normal drop data from `src/main/resources/droprates_clean.json` at startup.
- Rare Drop Table data is loaded from `src/main/resources/rare_drop_table.json`.
- Extra context rules and alternate-table metadata live in `src/main/resources/drop_metadata.json`.
- Minigame reward rates load from `src/main/resources/minigame_droprates.json`, keyed by the in-game reward source.
- Clue reward rates load from `src/main/resources/clue_droprates.json` and feed both the chat feed and the tooltips.
- Bosses whose drops the wiki stores outside a normal drop table load from `src/main/resources/special_droprates.json`.
- All data is bundled with the plugin. It makes no network requests while you play.
- The two main data files are regenerated from the OSRS Wiki's structured drop data with `python tools/crawl_bucket.py generate`; CI checks they stay in sync with the wiki.
- If an item exists in both the normal table and the RDT for the same NPC, and `Show source hints` is on, both rates are shown side by side.
- Monsters with several drop tables (Abyssal demon in the Wilderness Slayer Cave, Barbarian levels, Cyclops floors) are matched by the killed NPC's id. When the id cannot tell the tables apart, every table's rate is shown, labelled by version; `Show all table variants` turns that off.
- If an item and NPC combination is not in either database, no drop-rate message is shown.
- Messages appear in chat as `Quantity x Item (rate)`.
- Raids are not currently supported.

## Credits
Built and maintained by Pluckss.

- [@xTaig4](https://github.com/xTaig4) — rebuilt the drop data on the OSRS Wiki's structured
  Bucket API (`tools/crawl_bucket.py`) with a CI freshness check, and added per-version drop
  table matching by NPC id.

Drop rate data comes from the [OSRS Wiki](https://oldschool.runescape.wiki/), which is
licensed CC BY-NC-SA 3.0.
