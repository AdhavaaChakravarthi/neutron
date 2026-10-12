"""Builds app/src/main/assets/foods.tsv.gz from USDA SR Legacy (public domain).

Input: the SQLite build of SR Legacy published by nutratech/usda-sqlite (release 0.0.9), which loads the official
USDA ARS SR-Legacy Access database unchanged. Only the values below are taken; the GPL-licensed build scripts
are not used or redistributed.

    python3 build_food_db.py /path/to/usda.sqlite3

Columns: id, group, name, kcal, protein_g, fat_g, carb_g, fiber_g, sugar_g, sodium_mg (all per 100 g; blank when
USDA has no value), servings as "label=grams|label=grams".
"""
import gzip
import sqlite3
import sys
from pathlib import Path

NUTRIENTS = [208, 203, 204, 205, 291, 269, 307]   # kcal, protein, fat, carbohydrate, fiber, sugars, sodium
SKIP_GROUPS = {300}                                # baby foods
OUT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "foods.tsv.gz"


def clean(s: str) -> str:
    return s.replace("\t", " ").replace("\n", " ").replace("|", "/").replace("=", "-").strip()


def fmt(v):
    if v is None:
        return ""
    return f"{v:.3f}".rstrip("0").rstrip(".")


def main(db_path: str) -> None:
    c = sqlite3.connect(db_path)
    groups = dict(c.execute("select id, fdgrp_desc from fdgrp"))
    nut = {}
    for fid, nid, val in c.execute(f"select food_id, nutr_id, nutr_val from nut_data where nutr_id in ({','.join(map(str, NUTRIENTS))})"):
        nut.setdefault(fid, {})[nid] = val
    serv = {}
    for fid, desc, grams in c.execute("select s.food_id, d.msre_desc, s.grams from serving s join serv_desc d on d.id = s.msre_id order by s.food_id, s.msre_id"):
        if grams and grams > 0:
            serv.setdefault(fid, []).append(f"{clean(desc)}={grams:g}")
    rows = 0
    lines = ["id\tgroup\tname\tkcal\tprotein\tfat\tcarb\tfiber\tsugar\tsodium\tservings"]
    for fid, grp, name in c.execute("select id, fdgrp_id, long_desc from food_des order by id"):
        if grp in SKIP_GROUPS or fid not in nut or 208 not in nut[fid]:
            continue
        v = nut[fid]
        lines.append("\t".join([str(fid), clean(groups.get(grp, "")), clean(name)] + [fmt(v.get(n)) for n in NUTRIENTS]
                               + ["|".join(serv.get(fid, []))]))
        rows += 1
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(OUT, "wt", encoding="utf-8", compresslevel=9) as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote {rows} foods to {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main(sys.argv[1])
