# Drop Rate Plugin

## What this project is
- Live RuneLite plugin on the Plugin Hub (id: `drop-rate-properties`)
- Two parts: a Python crawler that scrapes the OSRS Wiki, and a Java RuneLite plugin
- The plugin shows item drop rates in two places:
  - **In chat** whenever you receive loot from an NPC / activity
  - **As a tooltip** when you hover an item in the Collection Log (shows every source + rate, grouped)

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
| `src/main/java/com/pluckss/droprate/DropRatePlugin.java` | Main plugin logic (chat feed + collection log tooltip) |
| `src/main/java/com/pluckss/droprate/DropRateConfig.java` | All config options shown in the RuneLite panel |
| `src/main/java/com/pluckss/droprate/ClogTooltipOverlay.java` | Overlay that draws the Collection Log hover tooltip via TooltipManager |
| `src/main/resources/droprates_clean.json` | NPC drop rates (613 NPCs) |
| `src/main/resources/rare_drop_table.json` | Rare Drop Table items (26 items) |
| `src/main/resources/drop_metadata.json` | NPC aliases, Ring of Wealth and Slayer task context rules |
| `src/main/resources/minigame_droprates.json` | Minigame/reward-chest drop rates, keyed by RuneLite LootReceived event name |
| `src/main/resources/clue_droprates.json` | Clue (Treasure Trail) reward rates per tier — tooltip only |
| `src/main/resources/special_droprates.json` | Bosses the main crawler structurally misses (Grotesque Guardians, Abyssal Sire Unsired rewards, Maggot King take-eggs pet route) |
| `icon.png` / `scripts/make_icon.py` | Plugin Hub icon (48×72 purple droplet) and its generator |
| `../Drop Rate Crawler/crawler_new.py` | Regenerates the NPC JSON data files from the OSRS Wiki |
| `../Drop Rate Crawler/minigame_crawler.py` | Separate crawler for minigame reward rates (`minigame_droprates.json`) |
| `../Drop Rate Crawler/clue_crawler.py` | Crawler for clue reward rates (`clue_droprates.json`) |
| `../Drop Rate Crawler/special_droprates.py` | Crawler for the special-case bosses (`special_droprates.json`) |

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

`clog_coverage.py` audits every in-game Collection Log item against the shipped
JSON and reports which ones have no rate, section by section. Run it after a
crawl to catch new content the crawler structurally misses:
```
py -3 clog_coverage.py             # audit the shipped plugin resources
py -3 clog_coverage.py --crawler   # audit a fresh crawl before copying
```
Sections with no honest fixed rate (raids, skilling pets, Sailing lost
schematics) are listed separately and are not counted as gaps.

`VerifyRates.java` runs every shipped rate through the plugin's own parsing
logic. Regenerate `all_rates.tsv` from the resources, then:
```
javac VerifyRates.java && java VerifyRates < all_rates.tsv
java VerifyRates "Maggot King" < all_rates.tsv   # spot-check by substring
```
It must report `FAILED=0`.

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

## Collection Log tooltip
- Hovering an item in the in-game Collection Log shows a tooltip listing every source that
  drops it, with rates, grouped by rate (identical rates collapse to one line; long groups
  and long tooltips are capped). `ClogTooltipOverlay` draws it via `TooltipManager` at
  `OverlayPosition.TOOLTIP`.
- Hook: `onMenuEntryAdded` filters to the Collection Log interface with
  `(entry.getParam1() >>> 16) == InterfaceID.COLLECTION` (621, gameval), reads the hovered
  item straight off the `MenuEntry`, and a 150 ms freshness window makes the tooltip vanish
  when the mouse leaves. No widget enumeration / per-frame hit-testing.
- Data: a tooltip-only `clogSources` map (item → sources) is built at load from
  `primaryDrops` + RDT + `clue_droprates.json`. It is kept SEPARATE from the chat feature's
  `invertedDrops` so the tooltip never changes chat behaviour.
- Config lives under the **Collection log** section (master toggle + hide-for-obtained).
- `clue_crawler.py` scrapes the 6 Reward casket pages. NO min-denominator filter (the tooltip
  wants every source, incl. common rewards). Decimal rates like `1/30.3` are CORRECT and kept
  as-is — clue caskets roll a variable number of times, so the per-casket chance is a genuine
  weighted average, exactly as the wiki shows it. Do not "normalise" them (false precision).
- **Deliberately shown WITHOUT a rate** (no honest fixed number exists): raid uniques
  (CoX/ToB/ToA — contribution/invocation-based) and skilling pets (per-activity + level). Those
  items simply get no tooltip rather than a fabricated one.

## Special-case bosses (`special_droprates.py`)
- The main NPC crawler only sees `{{DropsLine}}` tables on Category:Monsters pages, so it
  structurally misses bosses whose drops live elsewhere. `special_droprates.py` fills these and
  its output is merged into `primaryDrops` at load (one `mergeDropTable` line), feeding both
  chat and the tooltip:
  - **Grotesque Guardians** — loot table is on the combined activity page; the NPCs Dusk/Dawn
    have no DropsLine of their own. (Chat won't fire on Dusk/Dawn kills unless a
    `Dusk`/`Dawn` → `Grotesque Guardians` sourceAlias is added to `drop_metadata.json`.)
  - **Abyssal Sire** — the Sire only drops `Unsired` (1/100); the real rewards (bludgeon pieces,
    Jar of miasma, Abyssal orphan pet, ...) are on the `Unsired` page and are combined ×1/100.
  - **Maggot King (taking eggs)** — killing the boss lets you either loot the stomach (a normal
    DropsLine table, pet at 1/3500) or take eggs. The six egg variants each have their own drop
    rate AND their own hatch chance, and those live in a plain wikitable on the `Maggot egg`
    page, so the main crawler cannot see them. The per-kill chance for the egg route is
    sum(drop × pet) = 52/78125 = **1/1502.4**, i.e. 2.33× better than the stomach route. Kept as
    a decimal (a genuine weighted average, exactly as the wiki shows it — see the clue note
    above). It is a separate source key so the tooltip lists both routes side by side.
- If another boss turns up missing, add it here. Audit of 35 notable collection-log bosses found
  only the first two; the Maggot King egg route was found by `clog_coverage.py`.

## Name matching (why the crawler normalises names)
The plugin looks up NPC and item names by **exact string match** (`cleanName` only
strips colour tags and trims — there is no case folding or fuzzy matching). So any
name in the JSON that is not the exact in-game name is permanently unmatchable, in
both chat and the tooltip, and fails silently.

The wiki uses *page titles*, which carry disambiguation suffixes the game does not:
`Crawling hand (item)`, `Rock Golem (monster)`. `clean_item_name()` and
`clean_source_name()` in `crawler_new.py` strip only `(item)/(monster)/(npc)/
(disambiguation)/(page)`. Location suffixes like `Cyclops (God Wars Dungeon)` are
REAL variants mapped via `sourceAliases` and are deliberately left intact.

## Known data gaps
- **Herb / seed drop tables are not crawled at all.** 290 monsters are in
  `Category:Herb drop table monsters` and pages like Vorkath use
  `{{TreeHerbSeedDropLines|3/150|rolls=2|...}}`, which the `DropsLine[A-Za-z]*`
  pattern does not match (the name does not start with `DropsLine`). Those herb and
  seed drops are missing entirely. The template expands to ordinary `DropsLine`
  entries with `{{#expr:}}` rates, so the clean fix is to expand just that template
  server-side via the API (`action=expandtemplates`) and feed the result to the
  existing parser — no need to reimplement the template's arithmetic.
- **Legends' Quest RDT variant is unimplemented.** `drop_metadata.json` chains
  `X -> X RoW -> X RoW Legends`, but the crawler only ever emits `X` and `X RoW`, so
  all 65 `* Legends` lookup targets (and 33 of the 63 `* RoW` targets) resolve to
  nothing and fall through to the base rate. This is graceful — never a wrong rate —
  but it means `isLegendsQuestCompleted()` currently has no observable effect. The
  mechanic is real: reached *via the gem drop table*, the mega-rare table is replaced
  by a talisman unless Legends' Quest is done, so players without it see slightly
  optimistic rates for rune spear / dragon spear / shield left half.
- **Multi-table monsters keep only one rate in the plugin.** 602 entries across 179
  NPCs are JSON arrays (e.g. `Abyssal demon / Adamantite bar: ["2/128", "2/68"]`)
  because the wiki page has a separate table per location or variant.
  `loadPrimaryDrops` takes `arr.get(0)` and discards the rest, so a player killing
  the variant listed second sees the first variant's rate. `dropOverrides` in
  `drop_metadata.json` is the curated way to express these; it currently covers none
  of the 602.

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
