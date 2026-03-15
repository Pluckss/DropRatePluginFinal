# Drop Rate Plugin
Instantly see the exact drop rate of items when monsters or bosses drop them, without leaving the game.

Created by **Pluckss**.

## Example
![Drop Rate example](assets/hero-example.png)

## Features
- Shows a chat message when a dropped item has a known drop rate.
- Supports standard rates like `1/128` and bundle rates like `6/378` or `12/378`.
- Uses color-coded chat messages to quickly separate common, uncommon, and rare drops.
- Colors and rarity checks are based on the effective chance of the drop, including bundle drops.
- Works with drop data whether it is stored as `NPC -> item` or `item -> NPC`.
- Includes cleaner feed options so players can reduce chat clutter.

## Config
- `What to show`
  Choose between `Show all matched drops`, `Cleaner feed`, or `Rare drops only`.
- `Show common bundle drops`
  Shows multi-roll or bundled drops such as `4/115`, `6/378`, or `12/378`.
- `Rare drop threshold`
  Used only in `Rare drops only` mode. Example: `700` means show `1/700` and rarer.
- `Extra clutter items`
  Lets you hide custom filler items in `Cleaner feed` mode with a comma-separated list.

## Useful Notes
- The plugin reads its data from `src/main/resources/droprates_clean.json` at startup.
- If an item and NPC combination is not in the data, no drop-rate message is shown.
- Messages appear in chat as `Quantity x Item (rate)`.
- Raids are not currently supported.
