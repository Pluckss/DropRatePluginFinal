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
| `src/main/resources/droprates_clean.json` | NPC drop rates (592 NPCs) |
| `src/main/resources/rare_drop_table.json` | Rare Drop Table items (26 items) |
| `src/main/resources/drop_metadata.json` | NPC aliases, Ring of Wealth and Slayer task context rules |
| `../Drop Rate Crawler/crawler_new.py` | Regenerates the JSON data files from the OSRS Wiki |

## Regenerating data
```
py -3 crawler_new.py
# or for a quick test:
py -3 crawler_new.py --limit 100
```
Then copy the output files into `src/main/resources/`.

## Known data gaps
- Herb sub-table items are missing from `rare_drop_table.json` — herbs dropped via the RDT show no rate in chat

## Publishing an update to the Plugin Hub
1. Push your commit to `Pluckss/DropRatePluginFinal`
2. Get the full commit SHA: `git rev-parse HEAD`
3. Update `plugins/drop-rate-properties` in the `Pluckss/Drop-Rate` fork (which is a fork of `runelite/plugin-hub`)
4. The open PR at `runelite/plugin-hub/pull/11818` will pick up the change and re-run CI automatically
