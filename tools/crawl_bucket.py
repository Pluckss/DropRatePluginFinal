#!/usr/bin/env python3
"""Regenerate the bundled drop-rate data from the OSRS Wiki Bucket API.

The wiki renders every monster drop table from structured "buckets"
(https://meta.weirdgloop.org/w/Extension:Bucket). This script reads them and
writes droprates_clean.json and rare_drop_table.json under src/main/resources
in the exact shape the plugin already loads, so no Java changes are needed.

    dropsline           one row per drop line on every page
    drop_table_sources  each monster's access chance into the rare / gem tables
    infobox_monster     which pages are monsters
    infobox_npc         which pages are NPCs (pickpockets)
    RuneLite names.json in-game item names, to resolve wiki page titles

See CLAUDE.md "Regenerating data" for the rules that shape the output.

Usage
    python tools/crawl_bucket.py fetch      download the buckets into build/bucket-cache
    python tools/crawl_bucket.py generate   write the resource files from the cache
    python tools/crawl_bucket.py diff       compare a fresh generation with the committed files
    python tools/crawl_bucket.py check      exit 1 if the committed files are out of date (CI)

`generate`, `diff` and `check` fetch automatically when the cache is missing or
older than --max-age hours. Only stdlib is used so CI needs nothing installed.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import OrderedDict, defaultdict

WIKI_API = "https://oldschool.runescape.wiki/api.php"
# The wiki blocks default library user agents. Keep this descriptive.
USER_AGENT = "DropRatePlugin data crawler (+https://github.com/Pluckss/DropRatePluginFinal)"
PAGE_SIZE = 5000
REQUEST_GAP_SECONDS = 1.0

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESOURCES = os.path.join(ROOT, "src", "main", "resources")
CACHE_DIR = os.path.join(ROOT, "build", "bucket-cache")

PRIMARY_FILE = "droprates_clean.json"
RDT_FILE = "rare_drop_table.json"

# Rates rarer than 1/50 are shown in chat; anything more common is noise.
# Applied to the denominator alone, matching the original crawler.
MIN_DENOMINATOR = 50
EXCLUDED_ITEMS = {"Coins"}
EXCLUDED_PAGE_PATTERNS = [re.compile(r"\(Deadman\)$")]
# Drop lines the plugin can see as NPC loot: kills, pickpockets and implings.
# "reward" rows are kept only on pages that are also monsters (The Mimic,
# Yama, Vampyre Juvinate); on their own they are chests, packs and salvage.
# Skilling sources (trees, rocks, fishing spots) never arrive as NPC loot.
INCLUDED_DROP_TYPES = {"combat", "thieving", "hunter"}
MONSTER_ONLY_DROP_TYPES = {"reward"}
# Wiki page titles carry a disambiguator the in-game NPC name does not.
PAGE_SUFFIX_STRIP = re.compile(r" \(monster\)$")

# RuneLite's item name dump, used to turn wiki item page titles into the
# exact in-game names the plugin matches against.
RUNELITE_ITEM_NAMES_URL = "https://static.runelite.net/cache/item/names.json"
ITEM_NAMES_CACHE = "runelite_item_names"

# The primary file uses the Unicode multiplication sign, the rare-drop-table
# file uses an ASCII x. Both parse identically in the plugin; keeping the
# shipped convention keeps the diffs readable.
PRIMARY_TIMES = "×"
RDT_TIMES = "x"

FRACTION = re.compile(r"^(~?)\s*(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)$")


# ---------------------------------------------------------------- fetching

def bucket_query(query):
    url = WIKI_API + "?" + urllib.parse.urlencode(
        {"action": "bucket", "query": query, "format": "json"})
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=90) as resp:
                data = json.load(resp)
            if "error" in data:
                raise RuntimeError("bucket error: %s" % data["error"])
            return data.get("bucket", [])
        except (urllib.error.URLError, TimeoutError) as exc:
            if attempt == 3:
                raise
            print("  retry after %s" % exc, file=sys.stderr)
            time.sleep(5 * (attempt + 1))


def fetch_bucket(name, fields):
    rows = []
    offset = 0
    while True:
        query = "bucket('%s').select(%s).limit(%d).offset(%d).run()" % (
            name, ",".join("'%s'" % f for f in fields), PAGE_SIZE, offset)
        page = bucket_query(query)
        rows.extend(page)
        print("  %s: %d rows" % (name, len(rows)), file=sys.stderr)
        if len(page) < PAGE_SIZE:
            return rows
        offset += PAGE_SIZE
        time.sleep(REQUEST_GAP_SECONDS)


BUCKETS = {
    "dropsline": ["page_name", "page_name_sub", "item_name", "drop_json", "rare_drop_table"],
    "drop_table_sources": ["page_name", "page_name_sub", "table_name", "quantity", "rolls", "rarity", "drop_level", "drop_type"],
    # Pages with a monster or NPC infobox. Drop lines on any other page are
    # chests, stalls, birdhouses and the like, which never arrive as NPC loot.
    "infobox_monster": ["page_name"],
    "infobox_npc": ["page_name"],
}
CACHE_NAMES = list(BUCKETS) + [ITEM_NAMES_CACHE]


def cache_path(name):
    return os.path.join(CACHE_DIR, name + ".json")


def fetch_item_names():
    req = urllib.request.Request(RUNELITE_ITEM_NAMES_URL, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=90) as resp:
        names = json.load(resp)
    print("  runelite item names: %d" % len(names), file=sys.stderr)
    return names


def fetch_one(name):
    if name == ITEM_NAMES_CACHE:
        data = fetch_item_names()
    else:
        data = fetch_bucket(name, BUCKETS[name])
    with open(cache_path(name), "w", encoding="utf-8") as fh:
        json.dump(data, fh)


def fetch_all(names=None):
    os.makedirs(CACHE_DIR, exist_ok=True)
    for name in names or CACHE_NAMES:
        fetch_one(name)
        time.sleep(REQUEST_GAP_SECONDS)


def ensure_cache(max_age_hours):
    stale = []
    for name in CACHE_NAMES:
        path = cache_path(name)
        if not os.path.exists(path):
            print("cache missing: %s" % name, file=sys.stderr)
            stale.append(name)
            continue
        age = (time.time() - os.path.getmtime(path)) / 3600.0
        if max_age_hours is not None and age > max_age_hours:
            print("cache is %.1f h old: %s" % (age, name), file=sys.stderr)
            stale.append(name)
    if stale:
        fetch_all(stale)


def load_cache(name):
    with open(cache_path(name), encoding="utf-8") as fh:
        return json.load(fh)


# ---------------------------------------------------------------- parsing

def parse_rarity(text):
    """Return (approx, numerator_text, denominator_text) or None.

    Keeps the wiki's textual numbers so "1/416.7" and "4.5/249" survive as
    written. Thousands separators are stripped: "1/1,024" -> "1/1024".
    """
    if not text:
        return None
    m = FRACTION.match(text.replace(",", "").strip())
    if not m:
        return None
    approx, num, den = m.groups()
    return bool(approx), num, den


def round_denominator(den):
    """Shipped rare-drop rates keep one decimal: 2965.33 -> 2965.3, 8192.0 -> 8192."""
    value = round(float(den), 1)
    return str(int(value)) if value == int(value) else "%.1f" % value


def format_rate(num, den, rolls, approx, times):
    core = "%s/%s" % (num, den)
    if rolls and rolls > 1:
        core = "%s %s %s" % (rolls, times, core)
    return ("~" + core) if approx else core


def page_excluded(page):
    return any(p.search(page) for p in EXCLUDED_PAGE_PATTERNS)


def source_key(page):
    return PAGE_SUFFIX_STRIP.sub("", page)


class ItemNames:
    """Maps wiki item page titles to in-game item names.

    Most titles already are the in-game name. The rest are either a page
    anchor for one variant ("Bird nest (egg)#Blue egg") or a wiki-only
    disambiguator ("Bird nest (egg)") whose in-game name is the bare form.
    Titles that resolve to nothing are kept verbatim and reported.
    """

    def __init__(self, known_names):
        self.known = set(known_names)
        self.unresolved = set()

    def resolve(self, title):
        base = title.split("#", 1)[0].strip()
        if base in self.known:
            return base
        m = re.match(r"^(.*) \([^()]*\)$", base)
        if m and m.group(1) in self.known:
            return m.group(1)
        self.unresolved.add(base)
        return base


def is_table_row(row):
    # Rows generated by {{RareDropTable}} / {{GemDropTable}} carry the
    # rare_drop_table field (as an empty string); a monster's own rows do not.
    return row.get("rare_drop_table") is not None


# ---------------------------------------------------------------- primary

def build_primary(rows, item_names, npc_pages):
    """{monster: {item: rate | [rate, ...]}} from the monster's own rows."""
    table = defaultdict(lambda: defaultdict(list))
    combat_pages = {row["page_name"] for row in rows
                    if json.loads(row["drop_json"]).get("Drop type") == "combat"}
    for row in rows:
        page = row["page_name"]
        if page not in npc_pages or page_excluded(page) or is_table_row(row):
            continue
        info = json.loads(row["drop_json"])
        drop_type = info.get("Drop type")
        if drop_type not in INCLUDED_DROP_TYPES and not (
                drop_type in MONSTER_ONLY_DROP_TYPES and page in combat_pages):
            continue
        item = item_names.resolve(row["item_name"])
        if item in EXCLUDED_ITEMS:
            continue
        page = source_key(page)
        parsed = parse_rarity(info.get("Rarity"))
        if not parsed:
            continue
        approx, num, den = parsed
        if float(den) < MIN_DENOMINATOR:
            continue
        approx = approx or bool(info.get("Approx"))
        rate = format_rate(num, den, info.get("Rolls") or 1, approx, PRIMARY_TIMES)
        rates = table[page][item]
        if rate not in rates:
            rates.append(rate)
    return {
        page: {item: (rates[0] if len(rates) == 1 else rates)
               for item, rates in items.items()}
        for page, items in table.items() if items
    }


# ---------------------------------------------------------------- rare drop table

RDT_VARIANTS = ("", " RoW", " Legends", " RoW Legends")

# Gem drop table -> mega-rare table slot weights (Module:GemDropLines /
# Module:RareDropLines on the wiki). The wiki's displayed rates assume
# Legends' Quest is complete: the gem table's 1/128 mega-rare slot is live and
# the talisman slot is 3/128. Without the quest the mega-rare slot becomes a
# talisman instead, so the talisman is 4/128 and mega-rare items can only be
# reached through the rare drop table's own 15/128 slot.
MEGA_RARE_SLOTS = {"Rune spear": 8, "Shield left half": 4, "Dragon spear": 3}
TALISMANS = {"Chaos talisman", "Nature talisman"}
RDT_MEGA_SLOT = 15.0 / 128
MEGA_TOTAL = 128.0
MEGA_TOTAL_ROW = 15.0        # Ring of Wealth removes the 113 empty slots

# Pages whose drop tables must not be picked by page order. Rows arrive in page
# order and the first table on a page wins, which is wrong wherever the variants
# are separate NPCs with different table access. Cyclops is the one case in the
# data today: the Warriors' Guild Rooftop cyclopes (levels 56 and 76) reach only
# the gem drop table at 2/100, while the Basement ones (level 106, the Dragon
# defender drop) reach the full rare drop table at 2/100. Taking the rooftop
# table would report gem-table rates for every cyclops, up to 6.4x off.
RDT_PREFERRED_SUBPAGE = {
    "Cyclops": "Cyclops#Warriors' Guild Basement",
}
TALISMAN_NO_LEGENDS_FACTOR = 4.0 / 3


def access_rates(source_rows):
    """{(page, sub): {table_name: probability}} from drop_table_sources."""
    access = defaultdict(dict)
    for row in source_rows:
        parsed = parse_rarity(row.get("rarity"))
        if not parsed:
            continue
        sub = row.get("page_name_sub") or row["page_name"]
        prob = float(parsed[1]) / float(parsed[2])
        access[(row["page_name"], sub)].setdefault(row["table_name"], prob)
    return access


def rdt_rate(den_value, rolls):
    return format_rate("1", round_denominator(den_value), rolls, False, RDT_TIMES)


def build_rdt(rows, source_rows, item_names, npc_pages):
    """{item: {"<npc>", "<npc> RoW", "<npc> Legends", "<npc> RoW Legends": rate}}

    The wiki already multiplies each monster's access chance into the rare /
    gem table and stores the result on the monster's page as rows flagged
    rare_drop_table: Rarity is the plain rate and Alt Rarity the Ring of
    Wealth rate, both assuming Legends' Quest. The two no-quest variants are
    derived here. The first occurrence of an item on a page wins, matching
    the shipped file.
    """
    access = access_rates(source_rows)
    table = defaultdict(dict)
    for row in rows:
        page = row["page_name"]
        if page not in npc_pages or page_excluded(page) or not is_table_row(row):
            continue
        preferred = RDT_PREFERRED_SUBPAGE.get(page)
        if preferred is not None and row.get("page_name_sub") != preferred:
            continue
        info = json.loads(row["drop_json"])
        if info.get("Drop type") != "combat":
            continue
        item = item_names.resolve(row["item_name"])
        if item in EXCLUDED_ITEMS:
            continue
        base = parse_rarity(info.get("Rarity"))
        if not base:
            continue
        alt = parse_rarity(info.get("Alt Rarity")) or base
        rolls = info.get("Rolls") or 1
        key = source_key(page)
        if key in table[item]:
            continue
        legends_den, legends_row_den = float(base[2]) / float(base[1]), float(alt[2]) / float(alt[1])
        rates = {" Legends": rdt_rate(legends_den, rolls), " RoW Legends": rdt_rate(legends_row_den, rolls)}
        if item in TALISMANS:
            rates[""] = rdt_rate(legends_den / TALISMAN_NO_LEGENDS_FACTOR, rolls)
            rates[" RoW"] = rdt_rate(legends_row_den / TALISMAN_NO_LEGENDS_FACTOR, rolls)
        elif item in MEGA_RARE_SLOTS:
            sub = row.get("page_name_sub") or page
            tables = access.get((page, sub)) or next(
                (t for (p, _), t in access.items() if p == page), {})
            rdt_access = tables.get("Rare drop table")
            if rdt_access:
                slot = MEGA_RARE_SLOTS[item]
                rates[""] = rdt_rate(1 / (rdt_access * RDT_MEGA_SLOT * slot / MEGA_TOTAL), rolls)
                rates[" RoW"] = rdt_rate(1 / (rdt_access * RDT_MEGA_SLOT * slot / MEGA_TOTAL_ROW), rolls)
            # Otherwise the item is only reachable through the gem table's
            # mega-rare slot, which needs the quest: no no-quest variants.
        else:
            rates[""], rates[" RoW"] = rates[" Legends"], rates[" RoW Legends"]
        for suffix, rate in rates.items():
            table[item][key + suffix] = rate
    return dict(table)


# ---------------------------------------------------------------- validation

# Mirrors CHANCE_PATTERN in DropRatePlugin.parseChance, plus its rejection of
# anything containing "{" (an unexpanded wiki template).
PLUGIN_RATE = re.compile(r"^~?(?:(\d+(?:\.\d+)?)\s*[x×]\s*)?(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)$")


def validate(tables):
    """Return a list of (file, source, item, value) the plugin could not parse."""
    bad = []
    for name, table in tables.items():
        for outer, inner in table.items():
            for item, value in inner.items():
                for rate in (value if isinstance(value, list) else [value]):
                    if "{" in rate or not PLUGIN_RATE.match(rate):
                        bad.append((name, outer, item, rate))
    return bad


# ---------------------------------------------------------------- output

def dump_json(obj, path):
    """Match the shipped files: 2-space indent, sorted keys, CRLF, no final newline."""
    text = json.dumps(obj, indent=2, ensure_ascii=False, sort_keys=True)
    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text.replace("\n", "\r\n"))


def load_json(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def generate(rows_by_bucket, item_names):
    npc_pages = {row["page_name"] for name in ("infobox_monster", "infobox_npc")
                 for row in rows_by_bucket[name]}
    return {
        PRIMARY_FILE: build_primary(rows_by_bucket["dropsline"], item_names, npc_pages),
        RDT_FILE: build_rdt(rows_by_bucket["dropsline"], rows_by_bucket["drop_table_sources"], item_names, npc_pages),
    }


# ---------------------------------------------------------------- diff

def diff_tables(old, new):
    """Compare two {outer: {inner: value}} maps."""
    report = {"outer_added": [], "outer_removed": [], "added": [], "removed": [], "changed": [], "same": 0}
    for outer in sorted(set(old) | set(new)):
        if outer not in old:
            report["outer_added"].append((outer, len(new[outer])))
            report["added"].extend((outer, i, v) for i, v in new[outer].items())
            continue
        if outer not in new:
            report["outer_removed"].append((outer, len(old[outer])))
            report["removed"].extend((outer, i, v) for i, v in old[outer].items())
            continue
        o, n = old[outer], new[outer]
        for inner in sorted(set(o) | set(n)):
            if inner not in o:
                report["added"].append((outer, inner, n[inner]))
            elif inner not in n:
                report["removed"].append((outer, inner, o[inner]))
            elif o[inner] != n[inner]:
                report["changed"].append((outer, inner, o[inner], n[inner]))
            else:
                report["same"] += 1
    pair_renames(report)
    split_precision(report)
    return report


def effective_probability(value):
    if isinstance(value, list):
        value = value[0]
    m = PLUGIN_RATE.match(value)
    if not m:
        return None
    mult = float(m.group(1)) if m.group(1) else 1.0
    return mult * float(m.group(2)) / float(m.group(3))


def split_precision(report, tolerance=0.002):
    """Changes whose probability moved by less than `tolerance` (relative) are
    rounding differences, not new rates; list them separately."""
    real, precision = [], []
    for entry in report["changed"]:
        old, new = effective_probability(entry[2]), effective_probability(entry[3])
        if old and new and abs(old - new) / old < tolerance:
            precision.append(entry)
        else:
            real.append(entry)
    report["changed"], report["precision"] = real, precision


def rename_key(name):
    """Loose identity for an item name: case, page anchors and wiki-only
    disambiguators ("(tablet)", "(moon key)") are ignored."""
    base = name.split("#", 1)[0].strip().lower()
    return re.sub(r" \([^()]*\)$", "", base)


def pair_renames(report):
    """Move removed+added pairs that are the same drop under a corrected
    item name into report["renamed"]."""
    added_index = defaultdict(list)
    for entry in report["added"]:
        added_index[(entry[0], rename_key(entry[1]))].append(entry)
    renamed, still_removed, consumed = [], [], set()
    for entry in report["removed"]:
        key = (entry[0], rename_key(entry[1]))
        match = next((a for a in added_index.get(key, []) if id(a) not in consumed), None)
        if match is not None:
            consumed.add(id(match))
            renamed.append((entry[0], entry[1], match[1], entry[2], match[2]))
        else:
            still_removed.append(entry)
    report["renamed"] = renamed
    report["removed"] = still_removed
    report["added"] = [a for a in report["added"] if id(a) not in consumed]


def print_report(name, report, limit):
    print("== %s ==" % name)
    print("  unchanged lines : %d" % report["same"])
    print("  changed lines   : %d" % len(report["changed"]))
    print("  precision only  : %d  (same rate, last decimal differs)" % len(report["precision"]))
    print("  renamed items   : %d  (same drop, in-game item name corrected)" % len(report["renamed"]))
    print("  added lines     : %d  (in %d new sources)" % (len(report["added"]), len(report["outer_added"])))
    print("  removed lines   : %d  (in %d dropped sources)" % (len(report["removed"]), len(report["outer_removed"])))

    def show(title, entries, fmt):
        if not entries:
            return
        print("  -- %s (%d, showing up to %d)" % (title, len(entries), limit))
        for e in entries[:limit]:
            print("     " + fmt(e))

    show("changed", report["changed"], lambda e: "%s / %s: %s -> %s" % (e[0], e[1], e[2], e[3]))
    show("renamed", report["renamed"], lambda e: "%s / %s -> %s: %s%s" % (e[0], e[1], e[2], e[3], "" if e[3] == e[4] else " -> %s" % e[4]))
    show("removed", report["removed"], lambda e: "%s / %s: %s" % e)
    show("dropped sources", report["outer_removed"], lambda e: "%s (%d lines)" % e)
    show("new sources", report["outer_added"], lambda e: "%s (%d lines)" % e)
    show("added", report["added"], lambda e: "%s / %s: %s" % e)


def write_markdown_report(reports, path):
    lines = ["# Bucket crawl diff", ""]
    for name, r in reports.items():
        lines += ["## %s" % name, "",
                  "| | count |", "|---|---:|",
                  "| unchanged | %d |" % r["same"],
                  "| changed | %d |" % len(r["changed"]),
                  "| precision only | %d |" % len(r["precision"]),
                  "| renamed | %d |" % len(r["renamed"]),
                  "| added | %d |" % len(r["added"]),
                  "| removed | %d |" % len(r["removed"]),
                  "| new sources | %d |" % len(r["outer_added"]),
                  "| dropped sources | %d |" % len(r["outer_removed"]), ""]
        for title, key, fmt in (
                ("Changed", "changed", lambda e: "- %s / %s: `%s` -> `%s`" % (e[0], e[1], e[2], e[3])),
                ("Renamed", "renamed", lambda e: "- %s / %s -> %s: `%s`" % (e[0], e[1], e[2], e[4])),
                ("Removed", "removed", lambda e: "- %s / %s: `%s`" % e),
                ("Added", "added", lambda e: "- %s / %s: `%s`" % e)):
            if r[key]:
                lines += ["### %s" % title, ""] + [fmt(e) for e in r[key]] + [""]
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))


# ---------------------------------------------------------------- main

def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["fetch", "generate", "diff", "check"])
    ap.add_argument("--max-age", type=float, default=24.0, help="refetch when the cache is older than this many hours (default 24)")
    ap.add_argument("--limit", type=int, default=25, help="lines to show per diff section")
    ap.add_argument("--report", help="also write a markdown diff report to this path")
    args = ap.parse_args(argv)

    if args.command == "fetch":
        fetch_all()
        return 0

    ensure_cache(args.max_age)
    rows_by_bucket = {name: load_cache(name) for name in BUCKETS}
    item_names = ItemNames(load_cache(ITEM_NAMES_CACHE).values())
    generated = generate(rows_by_bucket, item_names)
    bad = validate(generated)
    if bad:
        print("%d rate strings the plugin cannot parse:" % len(bad), file=sys.stderr)
        for entry in bad[:args.limit]:
            print("  %s: %s / %s: %r" % entry, file=sys.stderr)
        return 2
    if item_names.unresolved:
        print("%d item titles not found in RuneLite's item names (kept verbatim):" % len(item_names.unresolved), file=sys.stderr)
        for title in sorted(item_names.unresolved)[:args.limit]:
            print("  " + title, file=sys.stderr)

    if args.command == "generate":
        for name, table in generated.items():
            dump_json(table, os.path.join(RESOURCES, name))
            print("wrote %s (%d sources)" % (name, len(table)))
        return 0

    reports = OrderedDict()
    for name, table in generated.items():
        path = os.path.join(RESOURCES, name)
        committed = load_json(path) if os.path.exists(path) else {}
        reports[name] = diff_tables(committed, table)
    for name, r in reports.items():
        print_report(name, r, args.limit)
    if args.report:
        write_markdown_report(reports, args.report)
        print("report written to %s" % args.report)

    if args.command == "check":
        dirty = any(r["changed"] or r["added"] or r["removed"] for r in reports.values())
        if dirty:
            print("resource files are out of date; run: python tools/crawl_bucket.py generate")
            return 1
        print("resource files are up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main())
