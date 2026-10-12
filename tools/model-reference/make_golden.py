"""Generate the golden vectors the Kotlin :model tests compare against.

    python3 make_golden.py          write golden/*.csv
    python3 make_golden.py --check  regenerate in memory and compare to golden/ (tolerance 1e-8);
                                    exits non-zero if the files are stale. Run in CI.
"""
from __future__ import annotations

import random
import sys
from pathlib import Path

from commander_model import (Episode, TpmParams, detect_rest_period, estimate_need, personal_acrophase,
                             plan_for_schedule, schedule_episodes, simulate, sleep_debt)

OUT = Path(__file__).resolve().parent / "golden"
TOL = 1e-8


def f(x) -> str:
    if x is None:
        return ""
    if isinstance(x, bool):
        return "1" if x else "0"
    if isinstance(x, int):
        return str(x)
    return f"{x:.10f}"


def files() -> dict[str, str]:
    out: dict[str, str] = {}

    # --- curves -----------------------------------------------------------
    restricted = schedule_episodes(23.5, 7.5, 15) + [
        Episode(24.0 * (k + 1) + 1.5, 24.0 * (k + 1) + 7.5) for k in range(15, 20)]
    curve_cases = {
        "baseline": (schedule_episodes(23.5, 7.5, 20), TpmParams(), 24.0 * 21),
        "restricted": (restricted, TpmParams(), 24.0 * 21),
        "late": (schedule_episodes(2.0, 10.0, 20), TpmParams(p=personal_acrophase(6.0)), 24.0 * 21),
    }
    for name, (eps, prm, t_end) in curve_cases.items():
        out[f"episodes_{name}.csv"] = "start,end\n" + "".join(f"{f(e.start)},{f(e.end)}\n" for e in eps)
        pts = simulate(eps, t_end, prm)
        rows = [f"# p={f(prm.p)} t_end={f(t_end)}", "i,t,asleep,s,alertness"]
        rows += [f"{i},{f(pt.t)},{f(pt.asleep)},{f(pt.s)},{f(pt.alertness)}"
                 for i, pt in enumerate(pts) if i % 3 == 0]
        out[f"curve_{name}.csv"] = "\n".join(rows) + "\n"

    # --- debt and need ----------------------------------------------------
    rng = random.Random(11)
    debt_rows = ["need,tst_newest_first,expected"]
    fixed = [(8.0, [8.0] * 14), (8.0, [7.0] * 14), (8.0, [5.0] + [8.0] * 13), (8.0, [8.0] * 13 + [5.0]),
             (8.0, [10.0] * 14), (8.5, [6.0, 9.0, 7.5]), (7.5, [])]
    rand = [(round(rng.uniform(7.0, 9.0), 2), [round(rng.uniform(4.0, 10.0), 2) for _ in range(rng.randint(1, 18))])
            for _ in range(12)]
    for need, tst in fixed + rand:
        debt_rows.append(f"{f(need)},{' '.join(f(x) for x in tst)},{f(sleep_debt(tst, need))}")
    out["debt.csv"] = "\n".join(debt_rows) + "\n"

    need_rows = ["prior,qualifying_tst,expected"]
    for prior, tst in [(8.0, []), (8.0, [9.0] * 15), (8.0, [7.0, 7.5]), (7.5, [8.25, 8.5, 9.0, 8.0])]:
        need_rows.append(f"{f(prior)},{' '.join(f(x) for x in tst)},{f(estimate_need(tst, prior))}")
    out["need.csv"] = "\n".join(need_rows) + "\n"

    acro_rows = ["midsleep,expected"]
    for mid in [3.0, 3.5, 0.0, 6.0, 23.0, 12.0, 9.5, 21.0]:
        acro_rows.append(f"{f(mid)},{f(personal_acrophase(mid))}")
    out["acrophase.csv"] = "\n".join(acro_rows) + "\n"

    # --- detector ---------------------------------------------------------
    bin_h = 5 / 60
    det_rows = ["bin_h,min_rest_h,start,end,counts"]

    def night(seed: int, on_h: float, off_h: float, rest_rate: float, wake_rate: float,
              checks: list[float]) -> list[int]:
        r = random.Random(seed)
        n = int(21 / bin_h)  # 18:00 -> 15:00
        on, off = int((on_h - 18) / bin_h), int((off_h - 18) / bin_h)
        counts = [sum(1 for _ in range(10) if r.random() < (rest_rate if on <= b < off else wake_rate) / 10)
                  for b in range(n)]
        for c in checks:
            counts[int((c - 18) / bin_h)] += 2
        return counts

    det_cases = [night(7, 23.75, 31.25, 0.02, 0.9, [27.0]),       # normal night, 3 a.m. check
                 night(8, 26.0, 34.0, 0.02, 0.9, []),             # 02:00 -> 10:00
                 night(9, 23.0, 30.0, 0.05, 0.6, [25.5, 28.0]),   # light user, two checks
                 night(10, 0.0, 0.0, 0.9, 0.9, []),               # phone used all night
                 [0] * int(21 / bin_h)]                           # phone never touched
    for counts in det_cases:
        res = detect_rest_period(counts, bin_h)
        s, e = (res if res else (-1, -1))
        det_rows.append(f"{f(bin_h)},{f(3.0)},{s},{e},{' '.join(str(c) for c in counts)}")
    out["detector.csv"] = "\n".join(det_rows) + "\n"

    # --- daily orders -----------------------------------------------------
    fields = ["p", "wake", "light_until", "groggy_until", "peak_start", "peak_at", "peak_end", "dip_at",
              "sleepy_from", "caffeine_cutoff", "melatonin_rise", "wind_down", "ideal_lights_out",
              "lights_out", "bed_step_limited", "target_wake", "next_wake", "wake_step_limited"]
    plan_rows = ["name,bed,wake,need,debt,target_wake,nights," + ",".join(fields)]
    plan_cases = [("steady", 23.5, 7.5, 8.0, 0.0, None),
                  ("short_late", 1.0, 7.0, 8.0, 0.0, None),
                  ("high_debt", 23.0, 7.0, 8.0, 6.0, None),
                  ("earlier_target", 23.5, 7.5, 8.0, 0.0, 6.0),
                  ("late_moderate_debt", 2.0, 10.0, 8.5, 3.0, None),
                  ("later_target", 22.5, 6.0, 7.5, 1.0, 7.0)]
    for name, b, w, n, d, tw in plan_cases:
        p = plan_for_schedule(b, w, n, d, tw)
        vals = [getattr(p, k) for k in fields]
        plan_rows.append(f"{name},{f(b)},{f(w)},{f(n)},{f(d)},{f(tw)},20," + ",".join(f(v) for v in vals))
    out["plan.csv"] = "\n".join(plan_rows) + "\n"
    return out


def same(a: str, b: str) -> bool:
    if a == b:
        return True
    try:
        x, y = float(a), float(b)
    except ValueError:
        return False
    return abs(x - y) <= TOL * max(1.0, abs(x))


def check(generated: dict[str, str]) -> list[str]:
    problems = []
    on_disk = {p.name for p in OUT.glob("*.csv")} if OUT.exists() else set()
    for extra in sorted(on_disk - set(generated)):
        problems.append(f"{extra}: not produced by make_golden.py")
    for name, text in generated.items():
        path = OUT / name
        if not path.exists():
            problems.append(f"{name}: missing")
            continue
        old, new = path.read_text().splitlines(), text.splitlines()
        if len(old) != len(new):
            problems.append(f"{name}: {len(old)} lines on disk, {len(new)} generated")
            continue
        for ln, (a, b) in enumerate(zip(old, new), 1):
            ca, cb = a.replace(" ", ",").split(","), b.replace(" ", ",").split(",")
            if len(ca) != len(cb) or not all(same(x, y) for x, y in zip(ca, cb)):
                problems.append(f"{name}:{ln}: differs")
                break
    return problems


if __name__ == "__main__":
    gen = files()
    if "--check" in sys.argv:
        bad = check(gen)
        for b in bad:
            print("STALE", b)
        print("golden vectors up to date" if not bad else f"{len(bad)} stale file(s): run make_golden.py")
        sys.exit(1 if bad else 0)
    OUT.mkdir(exist_ok=True)
    for name, text in gen.items():
        (OUT / name).write_text(text)
    print(f"wrote {len(gen)} files to {OUT}")
