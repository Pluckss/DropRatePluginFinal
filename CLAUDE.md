# Drop Rate Plugin

## What this project is
- Live RuneLite plugin on the Plugin Hub (id: `drop-rate-properties`)
- Two parts: a Python crawler that scrapes the OSRS Wiki, and a Java RuneLite plugin
- The plugin shows item drop rates in chat whenever you receive loot from an NPC

## How to run locally

### First time — copy Jagex credentials
1. Open Jagex Launcher, log into the account you want to test with, and launch RuneLite once
2. Run this in PowerShell:
```powershell
Copy-Item "$env:USERPROFILE\.runelite\credentials.properties" ".\DropRatePluginFinal\.runelite-dev-home\.runelite\credentials.properties" -Force
```

### Start the dev client
```powershell
cd DropRatePluginFinal
.\gradlew run
```

## Key files
| File | Purpose |
|---|---|
| `src/main/java/com/pluckss/droprate/DropRatePlugin.java` | Main plugin logic |
| `src/main/java/com/pluckss/droprate/DropRateConfig.java` | All config options shown in the RuneLite panel |
| `src/main/resources/droprates_clean.json` | NPC drop rates (608 NPCs) |
| `src/main/resources/rare_drop_table.json` | Rare Drop Table items (26 items) |
| `src/main/resources/drop_metadata.json` | NPC aliases, Ring of Wealth and Slayer task context rules |
| `src/main/resources/minigame_droprates.json` | Minigame/reward-chest drop rates, keyed by RuneLite LootReceived event name |
| `../Drop Rate Crawler/crawler_new.py` | Regenerates the NPC JSON data files from the OSRS Wiki |
| `../Drop Rate Crawler/minigame_crawler.py` | Separate crawler for minigame reward rates (`minigame_droprates.json`) |

## Regenerating data
```
py -3 crawler_new.py
# or for a quick test:
py -3 crawler_new.py --limit 100
```
Then copy the output files into `src/main/resources/`.

The crawler evaluates wiki rate templates ({{#expr:}}, {{#vardefine}}/{{#var}},
{{Brimstone rarity}}) and validates every emitted rate at the end of the run.
Check the end of the crawl output: it must say "every emitted rate is
plugin-parseable" with no WARNING lines before copying the files over.
`compare_crawls.py` (in the crawler folder) diffs a fresh crawl against the
currently shipped data — run it before shipping to spot lost NPCs/items.

## Minigame reward rates
- `minigame_crawler.py` scrapes reward tables for activities that deliver loot via a
  reward chest/pool/interface instead of an NPC kill. Output is merged into `primaryDrops`
  at load; an `onLootReceived` subscriber (filtered to `LootRecordType.EVENT`) feeds the
  same `handleLoot` path as NPCs. Each JSON key is the EXACT RuneLite event-name string.
- Shipped sources: Wintertodt, Tempoross, Guardians of the Rift, Soul Wars (pet only),
  Barbarian Assault high gamble (rares only).
- Deliberately NOT supported (no fixed wiki rates or no RuneLite event): Fishing Trawler
  (all "Varies"), Barrows (reward-potential rolls), Hallowed Sepulchre, Shades of Mort'ton,
  Zalcano, Hespori, The Gauntlet, raids. See memory `minigame-droprates.md` for the why.

## Known data gaps
- Herb sub-table items are missing from `rare_drop_table.json` — herbs dropped via the RDT show no rate in chat

## Publishing an update to the Plugin Hub
1. Push your commit to `Pluckss/DropRatePluginFinal`
2. Get the full commit SHA: `git rev-parse HEAD`
3. In the `Pluckss/Drop-Rate` fork (a fork of `runelite/plugin-hub`), create a
   branch **based on the current upstream `runelite/plugin-hub` master** — the
   fork's own master is usually stale, and branching from it causes merge
   conflicts on the PR
4. On that branch, set `commit=` in `plugins/drop-rate-properties` to the SHA
   from step 2, push the branch to the fork
5. Open a **new PR** against `runelite/plugin-hub` master (the original
   submission PR #11818 was merged 2026-05-08; each update needs a fresh PR)
6. Wait for CI to go green, then a RuneLite maintainer merges it — the update
   ships to users automatically
