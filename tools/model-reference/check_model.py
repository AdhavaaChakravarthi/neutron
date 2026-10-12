"""Behavioural checks for the reference model + the figure in docs/img.

Run:  python3 check_model.py            (prints results, exits non-zero on failure)
      python3 check_model.py --plot     (also writes ../../docs/img/energy-curves.png)
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

from commander_model import (KCAL_PER_KG, MAX_STEP_KCAL, Episode, FuelProfile, TpmParams, detect_rest_period,
                              estimate_need, fuel_check_ins, kss_from_alertness, losing_too_fast,
                              personal_acrophase, resting_energy, simulate, sleep_debt, targets_on, trend_series)

PRM = TpmParams()
failures: list[str] = []


def check(name: str, ok: bool, detail: str) -> None:
    print(f"[{'PASS' if ok else 'FAIL'}] {name}: {detail}")
    if not ok:
        failures.append(name)


def limitation(name: str, holds: bool, detail: str) -> None:
    """A behaviour the published model is known NOT to reproduce (MODEL.md section 7).
    Reported every run so it can't be forgotten; does not fail the build."""
    print(f"[LIMIT] {name}: {'reproduced' if holds else 'NOT reproduced'} - {detail}")


def nightly(start_clock: float, end_clock: float, first_night: int, nights: int) -> list[Episode]:
    """Episodes starting at start_clock on night k (k counted from day 0) and ending next morning."""
    eps = []
    for k in range(first_night, first_night + nights):
        # an after-midnight bedtime belongs to the next calendar day
        s = 24 * k + start_clock if start_clock >= 12 else 24 * (k + 1) + start_clock
        eps.append(Episode(s, 24 * (k + 1) + end_clock))
    return eps


def day_window(curve, day: int, wake_clock: float, hours: float):
    t0 = 24 * day + wake_clock
    return [pt for pt in curve if t0 <= pt.t < t0 + hours and pt.alertness is not None]


def mean_alert(pts) -> float:
    return sum(p.alertness for p in pts) / len(pts)


def local_extrema(pts):
    peaks, dips = [], []
    for a, b, c in zip(pts, pts[1:], pts[2:]):
        if b.alertness > a.alertness and b.alertness >= c.alertness:
            peaks.append(b)
        if b.alertness < a.alertness and b.alertness <= c.alertness:
            dips.append(b)
    return peaks, dips


def hhmm(t: float) -> str:
    c = t % 24
    return f"{int(c):02d}:{int(round((c % 1) * 60)) % 60:02d}"


# --- Scenario A: steady 23:30-07:30 ------------------------------------------
BASE_N = 20
base_eps = nightly(23.5, 7.5, 0, BASE_N)
base = simulate(base_eps, 24 * (BASE_N + 1))
wake_s = [next(p.s for p in base if p.t >= 24 * d + 7.5) for d in range(1, BASE_N + 1)]
check("S reaches steady state", abs(wake_s[-1] - wake_s[-2]) < 0.01,
      f"S at wake, last two days: {wake_s[-2]:.3f}, {wake_s[-1]:.3f}")

day = BASE_N - 1
win = day_window(base, day, 7.5, 16)
peaks, dips = local_extrema(win)
print("    baseline wake-window extrema:",
      "peaks", [(hhmm(p.t), round(p.alertness, 2)) for p in peaks],
      "dips", [(hhmm(p.t), round(p.alertness, 2)) for p in dips])
check("Groggy on waking", win[0].alertness < 7.0,
      f"alertness at wake {win[0].alertness:.2f} (sleepiness threshold 7)")
check("Single afternoon peak near the circadian acrophase",
      len(peaks) == 1 and 15.5 <= peaks[0].t % 24 <= 17.5,
      f"peak at {hhmm(peaks[0].t)}" if peaks else "no peak")
limitation("Rise-style afternoon dip and evening second wind", len(peaks) >= 2 and len(dips) >= 1,
           f"{len(peaks)} peak(s), {len(dips)} dip(s) with default U amplitude {PRM.ua}")
# Rate of rise slows in early afternoon even without a true dip: report where.
slopes = [(b.t, b.alertness - a.alertness) for a, b in zip(win, win[1:])]
mid = [s for s in slopes if 24 * day + 11 <= s[0] <= 24 * day + 16]
flat = min(mid, key=lambda s: s[1])
print(f"    slowest rise between 11:00 and 16:00 at {hhmm(flat[0])}")
kss_noon = kss_from_alertness(next(p for p in win if p.t >= 24 * day + 12).alertness, PRM)
check("KSS at noon is 'alert' territory", 1 <= kss_noon <= 5, f"KSS {kss_noon:.1f}")

# --- Scenario B: 5 nights of 6 h after baseline ------------------------------
RESTR_FROM = BASE_N - 5
restr_eps = nightly(23.5, 7.5, 0, RESTR_FROM) + nightly(1.5, 7.5, RESTR_FROM, 5)
restr = simulate(restr_eps, 24 * (BASE_N + 1))
r_days = [mean_alert(day_window(restr, d, 7.5, 16)) for d in range(RESTR_FROM + 1, BASE_N + 1)]
b_mean = mean_alert(win)
check("Restriction lowers daytime alertness", r_days[-1] < b_mean,
      f"baseline mean {b_mean:.2f} vs after 5 short nights {r_days[-1]:.2f}")
limitation("Deficit accumulates across short nights (Van Dongen 2003)", r_days[-1] < r_days[0] - 0.05,
           f"day-1 {r_days[0]:.2f} -> day-5 {r_days[-1]:.2f}")

# --- Scenario C: one 9 h recovery night after restriction --------------------
rec_eps = restr_eps + [Episode(24 * BASE_N + 22.5, 24 * (BASE_N + 1) + 7.5)]
rec = simulate(rec_eps, 24 * (BASE_N + 2))
rec_mean = mean_alert(day_window(rec, BASE_N + 1, 7.5, 16))
gap = b_mean - rec_mean
limitation("One recovery night is not enough (Banks 2010)", gap > 0.1,
           f"baseline {b_mean:.2f}, after one 9 h night {rec_mean:.2f} (gap {gap:.2f})")

# --- Scenario D: late sleeper 02:00-10:00 with personal acrophase -----------
p_late = personal_acrophase(6.0)
late_prm = TpmParams(p=p_late)
late_eps = nightly(2.0, 10.0, 0, BASE_N)
late = simulate(late_eps, 24 * (BASE_N + 1), late_prm)
lwin = day_window(late, BASE_N - 1, 10.0, 16)
lpk, ldp = local_extrema(lwin)
shift = ((lpk[0].t - peaks[0].t) % 24) if (lpk and peaks) else float("nan")
check("Late sleeper's curve shifts with them", bool(lpk) and abs(shift - 3.0) < 0.75,
      f"p={p_late:.1f}; peak at {hhmm(lpk[0].t) if lpk else '-'} (shift {shift:.2f} h vs 3.0 expected)")

# --- Sleep debt -------------------------------------------------------------
check("No debt when sleeping to need", sleep_debt([8.0] * 14, 8.0) == 0.0, "14 x 8 h vs need 8 h")
d1 = sleep_debt([7.0] * 14, 8.0)
check("1 h short for 14 nights", abs(d1 - 7.5) < 1e-9, f"debt {d1:.2f} h (sum of weights 105/14 = 7.5)")
d_recent = sleep_debt([5.0] + [8.0] * 13, 8.0)
d_old = sleep_debt([8.0] * 13 + [5.0], 8.0)
check("Recent nights count more", d_recent > d_old, f"last night short {d_recent:.2f} h vs 2 weeks ago {d_old:.2f} h")
check("Surplus pays debt down, floored at 0", sleep_debt([10.0] * 14, 8.0) == 0.0, "14 x 10 h")

# --- Sleep need -------------------------------------------------------------
check("Need = prior with no qualifying nights", estimate_need([]) == 8.0, "8.0 h")
n9 = estimate_need([9.0] * 15)
check("Need moves toward data", 8.7 < n9 < 9.0, f"15 natural-wake nights of 9 h -> {n9:.2f} h")

# --- Phone-only detection on a synthetic night -------------------------------
random.seed(7)
BIN = 5 / 60
bins = int(21 / BIN)       # 18:00 -> 15:00 next day
true_on, true_off = int((23.75 - 18) / BIN), int((31.25 - 18) / BIN)   # 23:45 -> 07:15
counts = []
for b in range(bins):
    rate = 0.02 if true_on <= b < true_off else 0.9
    counts.append(sum(1 for _ in range(10) if random.random() < rate / 10))
counts[true_on + 40] += 2   # a 3 a.m. phone check
res = detect_rest_period(counts, BIN)
if res:
    err_on, err_off = (res[0] - true_on) * 5, (res[1] - true_off) * 5
    check("Detects the night despite a 3 a.m. check", abs(err_on) <= 15 and abs(err_off) <= 15,
          f"onset error {err_on:+d} min, offset error {err_off:+d} min")
else:
    check("Detects the night despite a 3 a.m. check", False, "no rest period found")

# --- Fuel (MODEL.md section 10) ---------------------------------------------
check("Mifflin-St Jeor worked example", resting_energy(80, 180, 25, True) == 1805.0
      and resting_energy(80, 180, 25, False) == 1639.0, "80 kg, 180 cm, 25 y: 1805 kcal (men), 1639 kcal (women)")
tr = trend_series([(0, 80.0)] + [(d, 81.0) for d in range(1, 30)])
check("Trend moves 10% of the way each day", abs(tr[1] - 80.1) < 1e-9 and abs(tr[29] - (81 - 0.9 ** 29)) < 1e-9,
      f"after a 1 kg step: day 1 {tr[1]:.2f}, day 29 {tr[29]:.2f}")

FUEL_START = 20738   # a Monday


def fuel_sim(true_e: float, activity: str, rate: float, weeks: int, seed: int, logged_share: float = 1.0,
             log_every: int = 1, eat_offset: float = 0.0):
    """A person who eats their target (+/- 150 kcal, plus eat_offset) and weighs in daily (+/- 0.4 kg water).
    They log `logged_share` of what they eat, on every `log_every`-th day."""
    prof = FuelProfile(True, 22, 178, 85.0, activity, rate, FUEL_START)
    rng = random.Random(seed)
    w, weigh, intake, true_w = 85.0, [(FUEL_START, 85.0)], {}, []
    cis = []
    for d in range(FUEL_START, FUEL_START + 7 * weeks + 1):
        cis = fuel_check_ins(prof, weigh, intake, d)
        logged = targets_on(cis, d).kcal + rng.gauss(0, 150) + eat_offset
        if (d - FUEL_START) % log_every == 0:
            intake[d] = logged
        w += (logged / logged_share - true_e) / KCAL_PER_KG
        true_w.append(w)
        weigh.append((d + 1, w + rng.gauss(0, 0.4)))
    return cis, true_w


over, _ = fuel_sim(2700, "active", -0.005, 16, 1)    # starting guess ~3440 kcal, 27% high
late = [c.expenditure for c in over if c.day >= FUEL_START + 56]
check("Expenditure converges from a high first guess", all(abs(e - 2700) < 150 for e in late),
      f"true 2700 kcal; weeks 8-16 estimate {min(late):.0f}-{max(late):.0f}")
under, _ = fuel_sim(3200, "light", 0.0, 16, 2)       # starting guess ~2880 kcal, 10% low
late = [c.expenditure for c in under if c.day >= FUEL_START + 56]
check("Expenditure converges from a low first guess", all(abs(e - 3200) < 150 for e in late),
      f"true 3200 kcal; weeks 8-16 estimate {min(late):.0f}-{max(late):.0f}")
hide, true_w = fuel_sim(2700, "active", -0.005, 20, 3, logged_share=0.8)
rate = (true_w[-1] - true_w[-57]) / 8 / true_w[-57]
check("Under-logging by 20% still loses weight near the goal", -0.0060 < rate < -0.0015,
      f"goal -0.50%/wk; weeks 12-20 actual {rate * 100:.2f}%/wk (the resting-energy floor binds)")
sims = [over, under, hide, fuel_sim(2400, "vigorous", -0.01, 16, 4)[0], fuel_sim(2700, "active", 0.0025, 16, 5)[0]]
check("Target never below resting energy", all(c.kcal >= c.rest_kcal - 1e-9 for s in sims for c in s),
      f"{sum(len(s) for s in sims)} check-ins across 5 simulated people")
drops = [a.kcal - b.kcal for s in sims for a, b in zip(s, s[1:])]
check("Target never drops more than 200 kcal in a week", max(drops) <= MAX_STEP_KCAL + 1e-9,
      f"largest drop {max(drops):.0f} kcal")
sparse, _ = fuel_sim(2700, "active", -0.005, 8, 6, log_every=2)
check("No update from half-logged weeks", all(c.observed is None for c in sparse)
      and len({round(c.expenditure, 6) for c in sparse}) == 1, "logging every other day leaves the first guess alone")
crash, _ = fuel_sim(2700, "active", -0.005, 10, 7, eat_offset=-1300)
check("Losing faster than 1%/week is flagged", losing_too_fast(crash[-1]),
      f"eating ~1300 kcal under target: {crash[-1].weekly_rate * 100:.2f}%/wk")
check("Normal loss is not flagged", not any(losing_too_fast(c) for c in over),
      f"goal -0.5%/wk: fastest observed {min(c.weekly_rate for c in over if c.weekly_rate is not None) * 100:.2f}%/wk")

# --- Figure -----------------------------------------------------------------
if "--plot" in sys.argv:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    ink, ink2, muted, grid, surface = "#0b0b0b", "#52514e", "#898781", "#e1e0d9", "#fcfcfb"
    series = [("Steady 23:30–07:30", base, BASE_N - 1, 7.5, "#2a78d6"),
              ("After 5 nights of 6 h", restr, BASE_N, 7.5, "#eb6834"),
              ("Late sleeper 02:00–10:00", late, BASE_N - 1, 10.0, "#1baf7a")]
    fig, ax = plt.subplots(figsize=(9, 4.6), dpi=160)
    fig.patch.set_facecolor(surface)
    ax.set_facecolor(surface)
    for label, curve, d, wk, color in series:
        pts = day_window(curve, d, wk, 17)
        xs = [wk + (p.t - (24 * d + wk)) for p in pts]
        ys = [p.alertness for p in pts]
        ax.plot(xs, ys, color=color, lw=2, label=label, solid_capstyle="round")
        ax.annotate(label, (xs[-1], ys[-1]), xytext=(6, 0), textcoords="offset points",
                    va="center", fontsize=8, color=ink2)
    ax.axhline(7, color=muted, lw=1, ls=(0, (4, 3)))
    ax.text(12.0, 6.7, "sleepiness threshold (7)", fontsize=8, color=muted)
    ticks = list(range(6, 29, 2))
    ax.set_xticks(ticks, [f"{t % 24:02d}:00" for t in ticks], fontsize=8, color=muted)
    ax.tick_params(axis="y", labelsize=8, colors=muted)
    ax.set_xlim(6, 31.5)
    ax.set_ylabel("Predicted alertness (model units)", fontsize=9, color=ink2)
    ax.set_title("Commander energy curve: three-process model, default parameters",
                 fontsize=11, color=ink, loc="left")
    ax.grid(axis="y", color=grid, lw=0.6)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("left", "bottom"):
        ax.spines[s].set_color(grid)
    ax.legend(frameon=False, fontsize=8, loc="upper right", labelcolor=ink2)
    fig.tight_layout()
    out = Path(__file__).resolve().parents[2] / "docs" / "img" / "energy-curves.png"
    fig.savefig(out, facecolor=surface)
    print(f"    wrote {out}")

print(f"\n{len(failures)} failure(s)" if failures else "\nall checks passed")
sys.exit(1 if failures else 0)
