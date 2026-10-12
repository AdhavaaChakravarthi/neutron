"""Builds app/src/main/assets/exercises.tsv from free-exercise-db (public domain, the Unlicense).

Input: dist/exercises.json from github.com/yuhonas/free-exercise-db. Names, muscles, equipment and instructions
are kept; images are not.

    python3 build_exercise_db.py /path/to/exercises.json

Columns: id, name, category, equipment, level, mechanic, force, primary muscles, secondary muscles (comma-separated),
instructions (steps joined with " ¶ "). Plain text: the Android build unpacks .gz assets, so none is used.
"""
import json
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "exercises.tsv"


def clean(s) -> str:
    return ("" if s is None else str(s)).replace("\t", " ").replace("\r", " ").replace("\n", " ").replace("¶", " ").strip()


def main(path: str) -> None:
    data = json.load(open(path, encoding="utf-8"))
    lines = ["id\tname\tcategory\tequipment\tlevel\tmechanic\tforce\tprimary\tsecondary\tinstructions"]
    for x in sorted(data, key=lambda e: e["id"]):
        lines.append("\t".join([
            clean(x["id"]), clean(x["name"]), clean(x.get("category")), clean(x.get("equipment")), clean(x.get("level")),
            clean(x.get("mechanic")), clean(x.get("force")), ",".join(clean(m) for m in x.get("primaryMuscles", [])),
            ",".join(clean(m) for m in x.get("secondaryMuscles", [])),
            " ¶ ".join(clean(s) for s in x.get("instructions", []) if clean(s)),
        ]))
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {len(lines) - 1} exercises to {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main(sys.argv[1])
