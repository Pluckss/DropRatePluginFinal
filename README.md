# Drop Rate Plugin
Drop Rate is a RuneLite plugin that shows item drop-rate information for monsters and bosses directly in-game, so you can quickly check loot odds while playing.

Created by **Pluckss**.

## Features
- Shows a chat message when a dropped item has a known drop rate.
- Supports common rate formats like `1/128` and multi-roll formats like `2 x 1/128`.
- Color-codes drops by rarity:
  - Green: up to `1/300`
  - Orange: `1/301` to `1/999`
  - Red: `1/1000+`
- Looks up rates even when data is organized in either `NPC -> item` or `item -> NPC` direction.
- Optional filters for always-drops, common/filler drops, and spammy multi-roll drops.

## Config Options
- `Allow spam` (default: `false`)
  - Shows multi-roll/common drops such as `2/x`, `5/x`, `16/x`.
- `Only rare drops` (default: `false`)
  - Only shows drops at/above your threshold.
- `Rare drop threshold` (default: `500`)
  - Denominator cutoff used when `Only rare drops` is enabled.
- `Hide always drops` (default: `true`)
  - Hides guaranteed drops like `1/1`.
- `Hide useless drops` (default: `false`)
  - Hides filler drops (bones/ashes + custom list).
- `Useless items` (default: `Bones, Ashes, Zulrah's scales`)
  - Comma-separated item names to hide when `Hide useless drops` is enabled.

## Useful Notes
- The plugin reads data from `droprates_clean.json` at startup.
- If an item/NPC has no entry in the data, no drop-rate message is shown.
- Messages appear in game chat as `Quantity x Item (rate)`.
