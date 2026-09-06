# Drop Rate Plugin

## ⚠️ RULE 1 — TOP PRIORITY: audit against Plugin Hub rules BEFORE every submission

**Never open or update a plugin-hub PR without running the full checklist below first.**
This is not optional and not "probably fine because it passed last time".

Why this rule exists: on 2026-07-27 maintainer Alexsuperfly rejected PR #14341 with
*"your LICENSE file is malformed"*. The BSD 2-Clause text had been truncated part-way
through the warranty disclaimer — it ended at `CONSEQUENTIAL DAMAGES.` and dropped the
remaining six lines, so automated licence detection could not match it. **That flaw was
present from the initial commit and sailed through three previous merged PRs.** A green
`build` check does not prove compliance: the CI only compiles the plugin, it never
validates the licence, the manifest, or the icon.

Source of truth is <https://github.com/runelite/plugin-hub> (README + its own `LICENSE`).
**Re-read it each time — the rules change.** Do not rely on this list being current.

Run every check and paste the results before pushing:

| # | Requirement | How to verify |
|---|---|---|
| 1 | `LICENSE` is complete, unmodified **BSD 2-Clause** | `diff` it against `runelite/plugin-hub/LICENSE`; must be identical apart from the copyright line. Truncation is invisible by eye — always diff |
| 2 | `runelite-plugin.properties` has `displayName`, `author`, `description`, `tags`, `plugins`, `build` | `build` must be `standard` or `gradle`. `version` is optional (commit is used if absent) |
| 3 | `build.gradle` defines `pluginMainClass` | `grep pluginMainClass build.gradle` |
| 4 | `icon.png` ≤ **48×72 px** | read the PNG header, don't trust the filename |
| 5 | No template leftovers | `grep -rl "ExamplePlugin\|ExampleConfig\|com.example" src/` must be empty |
| 6 | `README.md` documents the features | must exist and be current |
| 7 | Repo public, HTTPS URL, **full 40-char** commit hash | check `plugins/drop-rate-properties` |
| 8 | Third-party deps have Gradle dependency verification | we currently add **no** third-party deps — keep it that way |
| 9 | Nothing that breaks Jagex's rules | informational only; must not aid combat |

Also, when reading the bot's `RuneLite Plugin Hub Checks` result:
- **`Changes are needed.`** → a real defect, fix it. Their README says this is the only one to worry about.
- **`Requires maintainer review.`** → a queue gate, not a defect. Nothing to fix; do NOT
  close/reopen or re-push, that only loses queue position. It renders as a red ❌ and can
  appear on a perfectly valid PR (on 2026-07-26 nine unrelated PRs had it simultaneously).
- **A red ❌ from the bot never means "our code is fine".** It reports its own gate, not a
  clean bill of health — run the checklist above regardless.

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
| `src/main/resources/npc_versions.json` | Monsters with several drop tables: each version's NPC ids and its own rates (generated) |
| `src/main/resources/drop_metadata.json` | NPC aliases, Ring of Wealth and Slayer task context rules |
| `src/main/resources/minigame_droprates.json` | Minigame/reward-chest drop rates, keyed by RuneLite LootReceived event name |
| `src/main/resources/clue_droprates.json` | Clue (Treasure Trail) reward rates per tier — chat feed + tooltips |
| `src/main/resources/special_droprates.json` | Bosses the main crawler structurally misses (Grotesque Guardians, Abyssal Sire Unsired rewards, Maggot King take-eggs pet route) |
| `icon.png` / `scripts/make_icon.py` | Plugin Hub icon (48×72 purple droplet) and its generator |
| `tools/crawl_bucket.py` | Regenerates `droprates_clean.json` and `rare_drop_table.json` from the OSRS Wiki Bucket API (see below) |
| `.github/workflows/drop-data.yml` | CI: fails a PR if those two files no longer match the wiki; also runs weekly |
| `../Drop Rate Crawler/minigame_crawler.py` | Separate crawler for minigame reward rates (`minigame_droprates.json`) |
| `../Drop Rate Crawler/clue_crawler.py` | Crawler for clue reward rates (`clue_droprates.json`) |
| `../Drop Rate Crawler/special_droprates.py` | Crawler for the special-case bosses (`special_droprates.json`) |
| `../Drop Rate Crawler/normalise_item_names.py` | Rewrites item names in the resources to the exact in-game spelling. **Run after every crawl** |
| `src/main/java/com/pluckss/droprate/ClueRewardTooltipOverlay.java` | Hover detection on the clue scroll reward screen (`WidgetItemOverlay`) |

## Regenerating data
`droprates_clean.json`, `rare_drop_table.json` and `npc_versions.json` are generated by
`tools/crawl_bucket.py` from the wiki's structured drop data (the Bucket API,
`api.php?action=bucket`, which is what the wiki itself renders drop tables
from). No wikitext parsing is involved, so new templates on the wiki cannot
silently drop lines.
```
python tools/crawl_bucket.py diff       # what would change vs the committed files
python tools/crawl_bucket.py generate   # rewrite the two files in src/main/resources
python tools/crawl_bucket.py check      # exit 1 if the committed files are stale (CI runs this)
```
Buckets are cached under `build/bucket-cache/` and refetched after 24 h
(`--max-age`). `diff --report build/diff.md` writes the full list. Stdlib only.

What the script does, so the output shape stays stable:
- Sources are wiki pages with a monster or NPC infobox; ` (monster)` is stripped
  from the key (`Manta ray (monster)` -> `Manta ray`). Deadman variants are skipped.
- Drop types kept: `combat`, `thieving` (pickpockets), `hunter` (implings), and
  `reward` only on pages that are also monsters (The Mimic, Yama).
- Item names are the wiki page title resolved against RuneLite's item name dump,
  so `Bird nest (egg)#Blue egg` -> `Bird nest` and `Annakarl teleport (tablet)` ->
  `Annakarl teleport`. Titles it cannot resolve are kept verbatim and listed.
- `Coins` is skipped and so is any rate whose denominator is below
  `MIN_DENOMINATOR = 50` (per array element, so `["1/78.4", "1/41.6"]` becomes `"1/78.4"`).
- One wiki row per item per page version, in page order, becomes an array.
  `Rolls` > 1 becomes the `N × A/B` prefix; `Approx` becomes the `~` prefix.
- Rare/gem-table rows are the ones the wiki flags `rare_drop_table`; they go to
  `rare_drop_table.json` (first occurrence per page wins), with `Rarity` as the
  ` Legends` variant and `Alt Rarity` as ` RoW Legends`. The wiki assumes
  Legends' Quest, so the no-quest variants are derived: talismans get the gem
  table's mega-rare slot (denominator x 3/4); Rune spear / Shield left half /
  Dragon spear keep only the rare-drop-table path (access x 15/128 x slot/128,
  RoW: slot/15). Monsters that reach the mega-rare table only via the gem table
  get no no-quest keys at all. Denominators are rounded to one decimal.
- `npc_versions.json` covers every monster whose page has more than one drop
  table: `{monster: [{version, ids, drops}]}`. A drop table is tied to the
  infobox versions (which carry NPC ids) by anchor name, else by the combat
  levels it lists. An id that would match several tables is left out, so the
  plugin shows all of them labelled instead of guessing.
- Every emitted string is checked against the plugin's `CHANCE_PATTERN`; the
  script exits 2 if anything would not parse.

`drop_metadata.json`, `minigame_droprates.json`, `clue_droprates.json` and
`special_droprates.json` are hand-curated / separately crawled and are not touched.

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

## Clue caskets (added 2026-09-06)
- Clue rewards used to be tooltip-only, so opening a master casket printed nothing however
  rare the reward was. `clue_droprates.json` is now merged into `primaryDrops` like the
  minigame data, so caskets report in chat through the normal filters.
- The join is `sourceAliases` in `drop_metadata.json`: RuneLite delivers casket loot as
  `LootReceived` / `LootRecordType.EVENT` named exactly **`Clue Scroll (Master)`** (and
  Beginner/Easy/Medium/Hard/Elite), which those aliases point at our `Master clue` keys.
  Those strings come from `LootTrackerPlugin`'s `CLUE_SCROLL_PATTERN` handler — if a name
  ever changes upstream, the aliases are the only thing to update.
- **Hovering an item on the reward screen** shows the same tooltip. The Collection Log's
  `MenuEntryAdded` trick does not work there: `InterfaceID.TRAIL_REWARDSCREEN` (73) is a
  display-only interface with five children and no per-item menu options, so its slots
  raise no menu entries. `ClueRewardTooltipOverlay` uses `WidgetItemOverlay` instead,
  which is handed each item's real canvas rectangle, and hit-tests the cursor itself.
  Its own config toggle is **Clue reward tooltips**.

## Chat colours vs the chatbox (added 2026-09-06)
- The standard chatbox is light parchment; the default uncommon orange (255,140,0) sits at
  ~1.2:1 contrast on it, which is what "the current orange text for mid tier drops makes it
  hard to read" meant. RuneLite solves this with two full colour sets (`ChatColorConfig`'s
  `opaqueXxx`/`transparentXxx`); doubling four pickers to eight was not worth it here.
- Instead `getChatColor` darkens the *user's own* picked colour toward black until it clears
  3:1 on parchment, keeping its hue. Only chat goes through it — the Collection Log tooltip
  draws on RuneLite's dark tooltip background and keeps the colours exactly as picked.
- Measured against the parchment: green 2.63→3.20, orange **1.20→3.19**, neutral white
  1.95→3.17. The default red and purple are already dark enough and come back untouched.
- Detection matches `ChatMessageManager`: `client.isResized() &&
  client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) != 0`. The `isResized()` guard
  matters — fixed mode is always the opaque chatbox whatever the varbit says.
- Toggle is **Fit standard chatbox** (Appearance), default on.

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

## The monster's own table always beats the RDT (fixed 2026-09-06)

`resolveDrop` used to walk the candidate list and return the first candidate that
matched *anything*. The context-resolved variants (`Araxxor Legends`,
`Demonic gorilla RoW`) exist only in `rare_drop_table.json`, so for any item on both
the monster's table and the RDT, the variant matched the RDT first and the monster's
own rate never got a look in:

- Araxxor / Rune kiteshield → `1/14720` (RDT) instead of `8/115`
- Demonic gorilla / Runite bar → `1/2560` instead of `15/500`, law/death runes likewise

It only bit players with a **Ring of Wealth equipped or Legends' Quest completed** —
without either, the resolved name is the plain NPC name and the bug is invisible, which
is why it survived so long. Measured over the whole dataset: **199 wrong rates across
72 sources**, including Vorkath, Zulrah, Cerberus, KBD, Kalphite Queen, Alchemical
Hydra and Abyssal demon. Now zero in all four RoW/Legends states.

`resolveDrop` now does two passes — normal table across every candidate first, RDT only
as a fallback — and looks the RDT rate up independently so the "Show source hints" line
still quotes the RoW/Legends variant's number.

`../Drop Rate Crawler/VerifyResolution.java` locks this in. It drives the **real**
plugin class against the **real** shipped JSON via reflection (no reimplementation; the
only stand-in is a `Proxy` over `DropRateConfig` returning the interface's own defaults),
so a rate can be checked without a game client. Build the plugin, then from the crawler
folder compile it against `build/classes/java/main` plus the compile classpath and run it
with `src/main/resources` on the classpath. It must print `FAILED=0`.

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
  - **Araxxor (supply table)** — `sub_threshold_drops()` recovers the drops that
    `MIN_DENOMINATOR = 50` filters out of the main crawl: Prayer potion(4) at 1/8,
    Super combat potion(1) / Shark / Wild pie at 1/16, and Brimstone key at 1/40.
    A supply drop lands on roughly a third of kills, so most corpse harvests handed the
    player an item the plugin had no rate for — reported as "when looting Araxxor corpse,
    drop rates do not show". It only emits items the normal parse did *not* already
    produce, so it can never overwrite a main-table rate (Araxyte venom sac is in both
    tables at different quantities and keeps its 5/115).
- If another boss turns up missing, add it here. Audit of 35 notable collection-log bosses found
  only the first two; the Maggot King egg route was found by `clog_coverage.py`.
- **The 1/50 floor is still global.** Araxxor is the only source whitelisted into
  `sub_threshold_drops`. Lowering `MIN_DENOMINATOR` for all 613 monsters would make the
  chat feed narrate every bone and arrow — a product decision, not a bug fix.

## Item names must be the game's exact spelling (fixed 2026-09-06)

The wiki writes many item names in sentence case; the game does not. Because lookups
are exact string matches, every one of those names is permanently dead — in chat and
in the tooltip — and fails silently. An audit against the game's own item name table
(`https://static.runelite.net/cache/item/names.json`, the same strings
`ItemComposition#getName` returns) found **83 wrong-case names across 187 entries**,
and they were the drops players most want announced: every boss pet
(`Pet snakeling` → `Pet Snakeling`, `Baby mole` → `Baby Mole`, `Ikkle hydra`,
`Prince black dragon`, `Jal-nib-rek` → `Jal-Nib-Rek`, `Tzrek-jad` → `TzRek-Jad`), every
jar (`Jar of venom` → `Jar of Venom`, miasma, stone, souls, swamp), every `3rd age` piece
→ `3rd Age`, and `Wine of zamorak` → `Wine of Zamorak` across 35 sources.

**Ownership is split — do not fix this in two places:**

- `droprates_clean.json` and `rare_drop_table.json` are regenerated wholesale from the
  wiki Bucket API by **PRs #10 / #11**, which already resolve names against the same
  `names.json`. Never hand-edit those two files: they are the largest in the repo and a
  rewrite makes the PRs unmergeable.
- `normalise_item_names.py` covers everything else — `minigame_droprates.json`,
  `special_droprates.json`, `clue_droprates.json` — and its file lists deliberately
  exclude the two PR-owned files. **Run it after every crawl, before copying files in.**

It only renames when the lowercased name maps to exactly one game name, so it can never
guess between two real items, and it leaves `Vet'ion jr.`/`Vet'ion Jr.` alone (the game
genuinely has both). It also reports names in no game item list at all — those are wiki
page-title suffixes the crawler does not strip (`Annakarl teleport (tablet)`,
`Bird nest (egg)`, `Tooth half of key (moon key)`, `Zombie head (Treasure Trails)`,
`Casket (Reward pool)`, the six `Maggot egg (…)` variants). Each needs a per-item
decision — some are real distinct items — so they are left untouched rather than guessed.

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

## Re-check on the wiki at the NEXT patch
New content ships with incomplete wiki data, so these had no honest rate on 2026-08-03 and
should be re-checked (and crawled again) before the next release. Run
`py -3 clog_coverage.py --crawler` after any crawl — that is what surfaces them.

| Item | Source | Why it has no rate today | What to check |
|---|---|---|---|
| `Ardeaglais teleport` | Mad Angel | Rate is **1/25**, below `MIN_DENOMINATOR = 50` in `crawler_new.py`, so it is filtered out. It IS a collection log item, so the tooltip shows nothing for it | Decide whether the clog tooltip should bypass the min-denominator filter the way `clue_crawler.py` already does. Lowering the threshold globally would change chat output for all 613 NPCs — don't do it blind |
| `Jeweller's chisel` | Golem crafting | Untradeable Golem crafting reward; the wiki publishes no rate at all | Re-read [[Golem crafting]] once the page leaves "under construction" |
| `Mr McGroot` | Goat hunting | Wiki states outright "the rates are currently unknown" | Re-read [[Goat hunting]]; if a rate appears, Goat hunting needs a `minigame_crawler.py` / `special_droprates.py` source |
| `Granite dust` | Mad Angel | `rarity=Always` (25–35 per kill) | Nothing to fix — a 100% drop has no meaningful rate. Listed only so it is not re-investigated every sweep |

`Mad Angel` was still tagged `Category:Articles under construction` when crawled, so its whole
table is worth a re-crawl once the page settles.

## Known data gaps
- **Herb / seed drop tables, Legends' Quest RDT variants** — both fixed by the
  Bucket crawler: herb/seed rows are ordinary `dropsline` rows, and
  `rare_drop_table.json` now carries all four `X` / `X RoW` / `X Legends` /
  `X RoW Legends` keys (see Regenerating data for how the no-quest ones are derived).
- **Multi-table monsters.** ~1,000 entries across ~200 NPCs are JSON arrays because
  the wiki page has a separate table per location or variant, or several rolls of
  one item on a single table. `loadPrimaryDrops` still keeps element 0 in
  `primaryDrops` (Collection Log tooltip, inverted lookups) but records the full
  list in `primaryVariants`. The chat feed goes through `resolveVersionedDrop`
  first: the killed NPC's id (from `NpcLootReceived` / `ServerNpcLoot`) picks the
  version out of `npc_versions.json`; if the id cannot decide (Goblin ids match
  both tables; minigame loot has no id) every version's rate is shown labelled,
  e.g. `Standard 2/128 | Wilderness Slayer Cave 2/68`; same-table multi-rolls
  are shown unlabelled. `dropOverrides` still wins when present. The config
  toggle `showVariantRates` turns the show-all fallback off (element 0 again).
  `tools/verify/VersionLookupCheck.java` exercises this against the shipped JSON
  through reflection (run instructions in its header); it must print `ALL OK`.
- **`MIN_DENOMINATOR` is denominator-based**, so `3.125/128` passes but the
  identical probability written `1/40.96` does not. Kept for now so chat
  behaviour does not shift; worth revisiting as a probability threshold.
- **Cyclops** has two Warriors' Guild tables; by id they resolve correctly, but the
  flat `Cyclops` key in `rare_drop_table.json` takes the first table on the page
  (Rooftop). Pin it in `dropOverrides` if Basement is preferred.

## Kill counter (real KC, fixed 2026-07-30)
- The chat feed's `KC:` used to be an in-memory session counter, which read exactly like the
  in-game kill count but reset on every restart/toggle/update. It is now the player's real KC.
- Source: RuneLite's stock **Chat Commands** plugin parses `Your X kill count is: N` and
  stores it per RS profile in config group `killcount`, key `boss.toLowerCase()`. We read it
  with `configManager.getRSProfileConfiguration("killcount", key, int.class)`.
- **Ordering is safe.** Verified live 5/5 at Alchemical Hydra (KC 511→515): the KC chat
  message is always dispatched *before* our loot event, so Chat Commands has already written
  the fresh value when we read it. No off-by-one, no need to defer output to end of tick.
- Key resolution is `killCountAliases` (in `drop_metadata.json`) → exact lowercase → lowercase
  with a leading `the ` stripped. The strip covers The Hueycoatl/Leviathan/Mimic/Nightmare/
  Whisperer, which the KC message names without the article. Aliases currently cover the three
  sources whose own name is nothing like the boss key (Wintertodt/Tempoross reward containers,
  Maggot King egg route).
- Sources that never print a KC message (most regular monsters) have no real count. Those fall
  back to the session counter and MUST stay labelled `KC: 8 this session` — an unlabelled
  session count next to `avg: ~1001 kills` is exactly the bug that was fixed.

## Known behaviour bugs (found 2026-07-28, NOT yet fixed — need live verification)
These three all need someone killing/thieving NPCs in a real client to verify, so they were
deliberately left out of the 2026-07-28 patch rather than shipped untested.

- **Pickpocketing reports combat drop rates.** The server sends NPC loot for pickpockets;
  RuneLite's own `LootTrackerPlugin` discards it with `ignorePickpocketLoot == client.getTickCount()`.
  We don't, and `Hero`, `Paladin`, `Guard`, `Man`, `Woman`, `Farmer`, `TzHaar-Hur` and
  `Knight of Ardougne` are all in `droprates_clean.json`. Worst case: pickpocketing a
  TzHaar-Hur prints `Uncut diamond (1/2048)` — the *kill* rate. The real thieving rate is
  `1/195`, which sits in element #2 of the array and is discarded (see below).
- **`NpcLootReceived` and `ServerNpcLoot` both fire for one kill.** `LootManager` posts them
  from two independent paths (`processNpcLoot` on despawn/item-spawn vs `processScriptLoot`
  from the `LOOTTRACKER_ADD_LOOT` script); neither suppresses the other. `LootTrackerPlugin`
  now has **zero** references to `NpcLootReceived` — upstream treats `ServerNpcLoot` as the
  complete path. Our `isDuplicateLoot` only saves us when the item multiset matches exactly
  within 1 tick. Before dropping `onNpcLootReceived`, verify on the NPCs that only reach loot
  through the client-side path: gargoyles/rockslugs/zygomites (die with >0 hp), Kraken,
  Nightmare and Duke Sucellus (delayed loot).
  **CONFIRMED LIVE 2026-07-30** at Alchemical Hydra: one kill produced two loot events in the
  same second, and `isDuplicateLoot` missed it because the two events describe the same drop
  differently — one listed `385 x1, 385 x1`, the other `385 x2`. So the multiset comparison is
  not just narrow, it is defeated by ordinary stacking. Over 5 real kills (KC 511→515) the
  session counter reached 6. Any per-kill counting built on these events must dedupe by
  *quantity-summed* item totals, not by the raw stack list.

## Publishing an update to the Plugin Hub
0. **Run the RULE 1 compliance checklist at the top of this file. Every time, no exceptions.**
   A passing `build` check does not mean the submission is compliant.
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
