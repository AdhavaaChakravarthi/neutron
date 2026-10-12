"""Commander reference model (Python).

This is the executable version of MODEL.md. It exists so the Kotlin port in
Phase 2 can be checked against golden vectors produced here. Keep the two in
sync: if a parameter or equation changes, change MODEL.md, this file, and the
Kotlin module together.

Sources for every parameter are in MODEL.md. Values marked RECONSTRUCTED or
ASSUMPTION there are flagged the same way here.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, replace
from typing import Iterable, Sequence

# ---------------------------------------------------------------------------
# 1. Energy curve: Three-Process Model of alertness (S + C + U + W)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class TpmParams:
    # Process S (homeostatic). Ingre et al. 2014.
    la: float = 2.4          # lower asymptote while awake
    ha: float = 14.3         # upper asymptote while asleep
    d: float = -0.0353       # wake decay rate, 1/h
    # Sleep recovery rate: S goes from 7.96 to 14.0 in 8 h. Ingre et al. 2014.
    g: float = math.log((14.3 - 14.0) / (14.3 - 7.96)) / 8.0   # ~ -0.3813 1/h
    # Process C (circadian, 24 h). Ingre et al. 2014.
    ca: float = 2.5
    cm: float = 0.0
    p: float = 16.8          # acrophase, decimal clock hours
    # Process U (12 h harmonic). Amplitude/mesor: Ingre et al. 2014.
    ua: float = 0.5
    um: float = -0.5
    u_trough_before_p: float = 3.0   # RECONSTRUCTED from Akerstedt, Folkard & Portin 2004
    # Process W (sleep inertia). Ingre et al. 2014.
    wc: float = -5.72
    wd: float = -1.51        # 1/h, time since waking
    # Alertness -> KSS. Ingre et al. 2014 default.
    kss_a: float = 10.6
    kss_b: float = -0.6


def process_c(clock_h: float, prm: TpmParams) -> float:
    return prm.cm + prm.ca * math.cos(2 * math.pi * (clock_h - prm.p) / 24.0)


def process_u(clock_h: float, prm: TpmParams) -> float:
    trough = prm.p - prm.u_trough_before_p
    return prm.um - prm.ua * math.cos(2 * math.pi * (clock_h - trough) / 12.0)


def process_w(hours_awake: float, prm: TpmParams) -> float:
    return prm.wc * math.exp(prm.wd * hours_awake)


def kss_from_alertness(a: float, prm: TpmParams) -> float:
    return prm.kss_a + prm.kss_b * a


@dataclass(frozen=True)
class Episode:
    """A sleep episode in absolute hours since the simulation origin (t=0 is midnight of day 0)."""
    start: float
    end: float


@dataclass
class CurvePoint:
    t: float            # absolute hours
    asleep: bool
    s: float
    alertness: float | None   # None while asleep


def simulate(episodes: Sequence[Episode], t_end: float, prm: TpmParams = TpmParams(),
             dt_h: float = 5 / 60, s0: float = 12.0) -> list[CurvePoint]:
    """Step Process S through wake and sleep and return the alertness curve.

    Exact exponential update per step, so the result does not depend on dt for S.
    s0 is arbitrary; run a burn-in of several days so it washes out.
    """
    eps = sorted(episodes, key=lambda e: e.start)
    out: list[CurvePoint] = []
    s = s0
    last_wake = 0.0
    n = int(round(t_end / dt_h))
    ei = 0
    for i in range(n + 1):
        t = i * dt_h
        while ei < len(eps) and eps[ei].end <= t:
            ei += 1
        asleep = ei < len(eps) and eps[ei].start <= t < eps[ei].end
        if asleep:
            out.append(CurvePoint(t, True, s, None))
        else:
            if ei > 0 and eps[ei - 1].end <= t:
                last_wake = eps[ei - 1].end
            clock = t % 24.0
            a = s + process_c(clock, prm) + process_u(clock, prm) + process_w(t - last_wake, prm)
            out.append(CurvePoint(t, False, s, a))
        # advance S to t + dt
        if asleep:
            s = prm.ha - (prm.ha - s) * math.exp(prm.g * dt_h)
        else:
            s = prm.la + (s - prm.la) * math.exp(prm.d * dt_h)
    return out


# ---------------------------------------------------------------------------
# 2. Personal circadian phase
# ---------------------------------------------------------------------------

REFERENCE_MIDSLEEP_H = 3.0   # ASSUMPTION: default p=16.8 treated as belonging to a ~23:00-07:00 sleeper
P_MIN, P_MAX = 13.0, 21.0    # ASSUMPTION: clamp so bad sleep data can't push the curve somewhere absurd


def personal_acrophase(habitual_midsleep_h: float, prm: TpmParams = TpmParams()) -> float:
    """Shift p by how far the user's habitual mid-sleep is from the reference. Prior only;
    replaced by calibration against KSS check-ins (MODEL.md section 5)."""
    shift = ((habitual_midsleep_h - REFERENCE_MIDSLEEP_H + 12) % 24) - 12
    return min(P_MAX, max(P_MIN, prm.p + shift))


# ---------------------------------------------------------------------------
# 3. Sleep need and sleep debt
# ---------------------------------------------------------------------------

DEFAULT_NEED_H = 8.0          # prior; see MODEL.md section 3
NEED_PRIOR_WEIGHT = 5.0       # ASSUMPTION: prior counts as 5 nights of evidence
DEBT_WINDOW = 14              # nights; matches the 14-day protocol of Van Dongen et al. 2003
DEBT_LOW_H = 2.0              # ASSUMPTION: "low debt" cut-off for using a night to estimate need


def debt_weights(n: int = DEBT_WINDOW) -> list[float]:
    """Linear recency weights: last night = 1.0, the oldest night in the window = 1/n."""
    return [(n - k) / n for k in range(n)]


def sleep_debt(tst_h_newest_first: Sequence[float], need_h: float) -> float:
    """Weighted 14-night deficit vs. need, in hours. Surplus nights pay debt down; floored at 0.
    Missing nights must be excluded by the caller (pass only nights with data)."""
    w = debt_weights()
    total = sum(wk * (need_h - tst) for wk, tst in zip(w, tst_h_newest_first[:DEBT_WINDOW]))
    return max(0.0, total)


def estimate_need(natural_wake_tst_h: Iterable[float], prior_h: float = DEFAULT_NEED_H) -> float:
    """Shrink the mean of qualifying nights toward the prior.
    Qualifying night: woke without an alarm AND debt before that night < DEBT_LOW_H."""
    xs = list(natural_wake_tst_h)
    if not xs:
        return prior_h
    return (NEED_PRIOR_WEIGHT * prior_h + sum(xs)) / (NEED_PRIOR_WEIGHT + len(xs))


# ---------------------------------------------------------------------------
# 4. Phone-only sleep detection (switchpoint model, after Cuttone et al. 2017)
# ---------------------------------------------------------------------------

def detect_rest_period(screen_on_counts: Sequence[int], bin_h: float,
                       min_rest_h: float = 3.0) -> tuple[int, int] | None:
    """Find the single interval [i, j) with a lower Poisson event rate than outside it,
    maximising the likelihood. Counts are screen-on events per bin across one night window.
    Returns (start_bin, end_bin) or None when no rest period of min_rest_h exists.
    """
    n = len(screen_on_counts)
    prefix = [0]
    for c in screen_on_counts:
        prefix.append(prefix[-1] + c)
    total = prefix[-1]
    min_bins = int(round(min_rest_h / bin_h))
    eps = 1e-3  # rate floor so log(0) never happens; one phone check doesn't kill the fit

    def ll(k: int, length: int) -> float:
        lam = max(k / length, eps)
        return k * math.log(lam) - lam * length

    best, best_ij = -math.inf, None
    for i in range(n):
        for j in range(i + min_bins, n + 1):
            k_in = prefix[j] - prefix[i]
            k_out = total - k_in
            len_in, len_out = j - i, n - (j - i)
            if len_out == 0:
                continue
            if k_in / len_in >= k_out / len_out:
                continue
            score = ll(k_in, len_in) + ll(k_out, len_out)
            if score > best:
                best, best_ij = score, (i, j)
    return best_ij


# ---------------------------------------------------------------------------
# 5. Daily orders (MODEL.md section 7)
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class PlanSettings:
    latency_h: float = 0.25          # ASSUMPTION: allowance to fall asleep
    caffeine_h: float = 8.0          # ASSUMPTION: Drake et al. 2013 shows 6 h is still too late
    wind_down_h: float = 1.0         # ASSUMPTION
    light_window_h: float = 1.0      # ASSUMPTION
    peak_band: float = 0.5           # ASSUMPTION: peak = within 0.5 units of the day's max
    groggy_w: float = -1.0           # ASSUMPTION: grogginess over once W > -1
    max_shift_h: float = 0.5         # ASSUMPTION: move bedtime / wake at most 30 min per day
    melatonin_lead_h: float = 2.0    # Sletten et al. 2010: sleep onset ~2 h after melatonin onset
    sleepy_threshold: float = 7.0    # Akerstedt, Folkard & Portin 2004


def debt_extension_h(debt_h: float) -> float:
    """Extra time in bed when debt is high. ASSUMPTION; bands match MODEL.md section 4."""
    if debt_h > 5.0:
        return 0.5
    if debt_h >= 2.0:
        return 0.25
    return 0.0


def wrap12(h: float) -> float:
    """Signed clock difference in (-12, 12]."""
    return ((h + 12.0) % 24.0) - 12.0


def schedule_episodes(bed_clock: float, wake_clock: float, nights: int) -> list[Episode]:
    """Night k starts at bed_clock on day k (day k+1 if bedtime is after midnight) and lasts
    (wake - bed) mod 24 hours. The last night ends on the morning of day `nights`."""
    dur = (wake_clock - bed_clock) % 24.0
    eps = []
    for k in range(nights):
        start = 24.0 * k + bed_clock if bed_clock >= 12.0 else 24.0 * (k + 1) + bed_clock
        eps.append(Episode(start, start + dur))
    return eps


@dataclass(frozen=True)
class DayPlan:
    p: float
    wake: float
    light_until: float
    groggy_until: float
    peak_start: float
    peak_at: float
    peak_end: float
    dip_at: float | None
    sleepy_from: float | None
    caffeine_cutoff: float
    melatonin_rise: float
    wind_down: float
    ideal_lights_out: float
    lights_out: float
    bed_step_limited: bool
    target_wake: float
    next_wake: float
    wake_step_limited: bool


def plan_day(episodes: Sequence[Episode], wake: float, habitual_onset: float, usual_next_wake: float,
             target_next_wake: float, need_h: float, debt_h: float,
             prm: TpmParams, st: PlanSettings = PlanSettings()) -> DayPlan:
    """All times are absolute hours. `episodes` must end with the night that ended at `wake`."""
    # Wake order: move toward the target, but never more than max_shift earlier than usual.
    next_wake = usual_next_wake + max(target_next_wake - usual_next_wake, -st.max_shift_h)
    wake_step_limited = next_wake > target_next_wake + 1e-9
    # Lights out: full need before next wake, never more than max_shift earlier than habitual.
    ideal = next_wake - need_h - st.latency_h - debt_extension_h(debt_h)
    lights_out = max(ideal, habitual_onset - st.max_shift_h)
    bed_step_limited = lights_out > ideal + 1e-9

    groggy_until = wake + math.log(prm.wc / st.groggy_w) / (-prm.wd)
    curve = simulate(episodes, lights_out, prm)
    window = [pt for pt in curve
              if groggy_until <= pt.t <= lights_out and pt.alertness is not None]
    peak_i = max(range(len(window)), key=lambda i: window[i].alertness)
    floor = window[peak_i].alertness - st.peak_band
    lo = peak_i
    while lo > 0 and window[lo - 1].alertness >= floor:
        lo -= 1
    hi = peak_i
    while hi < len(window) - 1 and window[hi + 1].alertness >= floor:
        hi += 1
    dips = [window[i] for i in range(1, len(window) - 1)
            if window[i].alertness < window[i - 1].alertness
            and window[i].alertness < window[i + 1].alertness]
    dip = min(dips, key=lambda pt: pt.alertness) if dips else None
    sleepy = next((pt.t for pt in window[peak_i:] if pt.alertness < st.sleepy_threshold), None)

    return DayPlan(
        p=prm.p, wake=wake, light_until=wake + st.light_window_h, groggy_until=groggy_until,
        peak_start=window[lo].t, peak_at=window[peak_i].t, peak_end=window[hi].t,
        dip_at=dip.t if dip else None, sleepy_from=sleepy,
        caffeine_cutoff=lights_out - st.caffeine_h,
        melatonin_rise=habitual_onset - st.melatonin_lead_h,
        wind_down=lights_out - st.wind_down_h,
        ideal_lights_out=ideal, lights_out=lights_out, bed_step_limited=bed_step_limited,
        target_wake=target_next_wake, next_wake=next_wake, wake_step_limited=wake_step_limited)


def plan_for_schedule(bed_clock: float, wake_clock: float, need_h: float, debt_h: float = 0.0,
                      target_wake_clock: float | None = None, nights: int = 20,
                      prm: TpmParams = TpmParams(), st: PlanSettings = PlanSettings()) -> DayPlan:
    """Today's orders when the only data is the user's usual schedule (pre-detection).
    Today is day `nights`; the circadian phase is the personal first guess from mid-sleep."""
    dur = (wake_clock - bed_clock) % 24.0
    mid = (bed_clock + dur / 2.0) % 24.0
    prm = replace(prm, p=personal_acrophase(mid, prm))
    eps = schedule_episodes(bed_clock, wake_clock, nights)
    wake = eps[-1].end
    habitual_onset = 24.0 * nights + bed_clock if bed_clock >= 12.0 else 24.0 * (nights + 1) + bed_clock
    usual_next_wake = habitual_onset + dur
    tw = wake_clock if target_wake_clock is None else target_wake_clock
    target_next_wake = usual_next_wake + wrap12(tw - wake_clock)
    return plan_day(eps, wake, habitual_onset, usual_next_wake, target_next_wake, need_h, debt_h, prm, st)


# ---------------------------------------------------------------------------
# 6. Real nights (MODEL.md section 9)
# ---------------------------------------------------------------------------

def _median(xs: Sequence[float]) -> float:
    s = sorted(xs)
    n = len(s)
    return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2.0


def _after_noon(clock_h: float) -> float:
    """Clock hour re-expressed as hours after noon, so 23:00 and 01:00 sit next to each other."""
    return (clock_h - 12.0) % 24.0


def wake_day(e: Episode) -> int:
    return int(math.floor(e.end / 24.0))


HABIT_WINDOW = 7       # ASSUMPTION: habitual times come from the last 7 recorded nights
HABIT_MIN_NIGHTS = 3   # ASSUMPTION: fewer than 3 recorded nights -> use the stated usual schedule


def habitual_times(nights: Sequence[Episode], fallback_bed: float, fallback_wake: float) -> tuple[float, float]:
    """Median sleep-onset and wake clock times of the last HABIT_WINDOW recorded nights."""
    recent = sorted(nights, key=lambda e: e.end)[-HABIT_WINDOW:]
    if len(recent) < HABIT_MIN_NIGHTS:
        return fallback_bed, fallback_wake
    onset = (_median([_after_noon(e.start % 24.0) for e in recent]) + 12.0) % 24.0
    wake = _median([e.end % 24.0 for e in recent])
    return onset, wake


def nights_with_fill(nights: Sequence[Episode], today: int, bed_clock: float, wake_clock: float,
                     days: int = 20) -> list[Episode]:
    """One night per wake day for the last `days` days: the recorded night if there is one (the longest, if
    several), otherwise the habitual schedule as a provisional night."""
    by_day: dict[int, Episode] = {}
    for e in nights:
        d = wake_day(e)
        if d not in by_day or (e.end - e.start) > (by_day[d].end - by_day[d].start):
            by_day[d] = e
    dur = (wake_clock - bed_clock) % 24.0
    out = []
    for d in range(today - days + 1, today + 1):
        if d in by_day:
            out.append(by_day[d])
        else:
            end = 24.0 * d + wake_clock
            out.append(Episode(end - dur, end))
    return out


def step_anchors(nights: Sequence[Episode], today: int, bed_c: float, wake_c: float) -> tuple[float, float]:
    """Where the 30-minute steps start from: the earlier of the habitual time and last night's time. Following
    orders moves you on the next day; one late night can't pull the plan later. ASSUMPTION (D-44)."""
    last = [e for e in nights if wake_day(e) == today]
    if not last:
        return bed_c, wake_c
    e = max(last, key=lambda x: x.end - x.start)
    bed = (min(_after_noon(bed_c), _after_noon(e.start % 24.0)) + 12.0) % 24.0
    wake = min(wake_c, e.end % 24.0)
    return bed, wake


def plan_from_history(nights: Sequence[Episode], naps: Sequence[Episode], today: int,
                      usual_bed: float, usual_wake: float, need_h: float, debt_h: float = 0.0,
                      target_wake_clock: float | None = None, days: int = 20,
                      prm: TpmParams = TpmParams(), st: PlanSettings = PlanSettings()) -> DayPlan:
    """Today's orders from recorded nights. With no recorded nights this equals plan_for_schedule()."""
    past = [e for e in nights if wake_day(e) <= today]
    bed_c, wake_c = habitual_times(past, usual_bed, usual_wake)
    filled = nights_with_fill(past, today, bed_c, wake_c, days)
    keep_naps = [n for n in naps
                 if filled[0].start <= n.start and not any(n.start < f.end and f.start < n.end for f in filled)]
    eps = sorted(filled + keep_naps, key=lambda e: e.start)
    wake = filled[-1].end
    dur = (wake_c - bed_c) % 24.0
    mid = (bed_c + dur / 2.0) % 24.0
    prm = replace(prm, p=personal_acrophase(mid, prm))
    bed_a, wake_a = step_anchors(past, today, bed_c, wake_c)
    habitual_onset = 24.0 * today + bed_a if bed_a >= 12.0 else 24.0 * (today + 1) + bed_a
    usual_next_wake = 24.0 * (today + 1) + wake_a if wake_a < bed_a or bed_a < 12.0 else 24.0 * (today + 1) + wake_a
    tw = wake_a if target_wake_clock is None else target_wake_clock
    target_next_wake = usual_next_wake + wrap12(tw - wake_a)
    return plan_day(eps, wake, habitual_onset, usual_next_wake, target_next_wake, need_h, debt_h, prm, st)


def daily_sleep(nights: Sequence[Episode], naps: Sequence[Episode], skipped_days: Sequence[int],
                today: int, lookback: int = 30) -> list[float]:
    """Total sleep per wake day, newest first, for days with a recorded night or a 'didn't sleep' mark.
    Naps count toward the day they end on (D-16). Days without data are left out, not counted as zero."""
    out = []
    for d in range(today, today - lookback, -1):
        has_night = any(wake_day(e) == d for e in nights)
        if not has_night and d not in skipped_days:
            continue
        total = sum(e.end - e.start for e in nights if wake_day(e) == d)
        total += sum(n.end - n.start for n in naps if wake_day(n) == d)
        out.append(total)
    return out


def need_from_history(nights: Sequence[Episode], natural: Sequence[bool], naps: Sequence[Episode],
                      skipped_days: Sequence[int], prior_h: float = DEFAULT_NEED_H) -> float:
    """MODEL.md section 3: a night qualifies when you woke without an alarm and debt before it (against the
    prior) was under DEBT_LOW_H. natural[i] belongs to nights[i]."""
    qualifying = []
    for e, nat in zip(nights, natural):
        if not nat:
            continue
        d = wake_day(e)
        before = daily_sleep(nights, naps, skipped_days, d - 1)
        if sleep_debt(before, prior_h) < DEBT_LOW_H:
            qualifying.append(e.end - e.start)
    return estimate_need(qualifying, prior_h)


def sleep_regularity_index(nights: Sequence[Episode], naps: Sequence[Episode], today: int,
                           days: int = 7, step_h: float = 5 / 60) -> float | None:
    """Phillips et al. 2017: probability of being in the same state (asleep/awake) 24 h apart, scaled so that
    perfectly regular = 100 and random = 0 (SRI = 200 * P(same) - 100). Compared only across consecutive wake
    days that both have a recorded night; noon-to-noon windows. None with fewer than 3 such pairs."""
    recorded = {wake_day(e) for e in nights}
    eps = list(nights) + list(naps)

    def asleep(t: float) -> bool:
        return any(e.start <= t < e.end for e in eps)

    same = total = pairs = 0
    for d in range(today - days + 1, today + 1):
        if d not in recorded or (d - 1) not in recorded:
            continue
        pairs += 1
        t = 24.0 * (d - 2) + 12.0
        n = int(round(24.0 / step_h))
        for i in range(n):
            a = asleep(t + i * step_h)
            b = asleep(t + i * step_h + 24.0)
            same += a == b
            total += 1
    if pairs < 3:
        return None
    return 200.0 * same / total - 100.0


def mid_sleep(e: Episode) -> float:
    """Mid-sleep clock time, as hours after noon (so values compare across midnight)."""
    return _after_noon(((e.start + e.end) / 2.0) % 24.0)


def social_jetlag(nights: Sequence[Episode], free_wake_days: Sequence[int]) -> float | None:
    """Wittmann et al. 2006: |mid-sleep on free days - mid-sleep on workdays|, hours. Free night = the night
    before a free day (it ends on a free wake day). Needs >= 2 free and >= 3 work nights."""
    free = [mid_sleep(e) for e in nights if wake_day(e) in free_wake_days]
    work = [mid_sleep(e) for e in nights if wake_day(e) not in free_wake_days]
    if len(free) < 2 or len(work) < 3:
        return None
    return abs(_median(free) - _median(work))


def chronotype_msfsc(nights: Sequence[Episode], natural: Sequence[bool], free_wake_days: Sequence[int],
                     free_days_per_week: int) -> float | None:
    """MCTQ chronotype (Roenneberg et al. 2003/2012): mid-sleep on free days, corrected for catch-up sleep.
    MSFsc = MSF if SDf <= SDw, else MSF - (SDf - SDweek)/2, SDweek = (SDw*WD + SDf*FD)/7.
    Not available when most free nights ended with an alarm. Returns a clock hour."""
    free = [(e, n) for e, n in zip(nights, natural) if wake_day(e) in free_wake_days]
    work = [e for e in nights if wake_day(e) not in free_wake_days]
    if len(free) < 2 or len(work) < 3:
        return None
    if sum(1 for _, n in free if not n) * 2 > len(free):
        return None
    msf = _median([mid_sleep(e) for e, _ in free])
    sdf = sum(e.end - e.start for e, _ in free) / len(free)
    sdw = sum(e.end - e.start for e in work) / len(work)
    fd = free_days_per_week
    sdweek = (sdw * (7 - fd) + sdf * fd) / 7.0
    msfsc = msf if sdf <= sdw else msf - (sdf - sdweek) / 2.0
    return (msfsc + 12.0) % 24.0


# ---------------------------------------------------------------------------
# 7. Fuel (MODEL.md section 10)
# ---------------------------------------------------------------------------
# Days are integer epoch days (1970-01-01 = 0, a Thursday), as LocalDate.toEpochDay() in Kotlin.

KCAL_PER_KG = 7700.0          # ~3500 kcal/lb = 32.2 MJ/kg, the conventional rule examined by Hall 2008
TREND_ALPHA = 0.1             # Hacker's Diet: exponentially smoothed moving average, 10% smoothing
PAL = {"light": 1.55, "active": 1.85, "vigorous": 2.20}   # ASSUMPTION: midpoints of the FAO/WHO/UNU 2001 bands
E_MIN_FACTOR = 1.0            # ASSUMPTION: expenditure is never estimated below resting energy
E_MAX_FACTOR = 2.5            # ASSUMPTION: FAO/WHO/UNU 2001 say PAL > 2.40 is difficult to maintain; widened for error
WINDOW_DAYS = 21              # ASSUMPTION: expenditure looks back 21 days
MIN_LOGGED_DAYS = 14          # ASSUMPTION
MIN_WEIGHED_DAYS = 8          # ASSUMPTION
MIN_WEIGH_SPAN = 14           # ASSUMPTION: first and last weigh-in in the window at least 14 days apart
BLEND = 0.5                   # ASSUMPTION: each check-in moves the estimate halfway to what the data imply
MAX_STEP_KCAL = 200.0         # ASSUMPTION: largest drop in the kcal target per check-in (rule of command 3)
PROTEIN_G_PER_KG = 1.6        # Morton et al. 2018: no further FFM gain beyond 1.62 g/kg/day
FAT_SHARE = 0.25              # ASSUMPTION inside the IOM 2005 AMDR of 20-35 % of energy
MAX_LOSS_RATE = 0.01          # Helms et al. 2014: 0.5-1 % of body weight per week
MAX_GAIN_RATE = 0.005         # ASSUMPTION


def weekday(day: int) -> int:
    """ISO weekday, Monday = 1 ... Sunday = 7."""
    return (day + 3) % 7 + 1


def resting_energy(weight_kg: float, height_cm: float, age_y: float, male: bool) -> float:
    """Mifflin-St Jeor 1990: REE = 10 W + 6.25 H - 5 A + 5 (men) or - 161 (women), kcal/day."""
    return 10.0 * weight_kg + 6.25 * height_cm - 5.0 * age_y + (5.0 if male else -161.0)


def daily_weights(weigh_ins: Sequence[tuple[int, float]]) -> dict[int, float]:
    """Mean weigh-in per day."""
    acc: dict[int, list[float]] = {}
    for d, kg in weigh_ins:
        acc.setdefault(d, []).append(kg)
    return {d: sum(v) / len(v) for d, v in sorted(acc.items())}


def trend_series(weigh_ins: Sequence[tuple[int, float]]) -> dict[int, float]:
    """Trend weight for every day from the first weigh-in to the last. Gaps are filled by straight lines between
    weigh-ins (ASSUMPTION), then T_d = T_(d-1) + 0.1 (w_d - T_(d-1)), starting at the first weigh-in."""
    w = daily_weights(weigh_ins)
    if not w:
        return {}
    days = sorted(w)
    filled = {days[-1]: w[days[-1]]}
    for a, b in zip(days, days[1:]):
        for d in range(a, b):
            filled[d] = w[a] + (w[b] - w[a]) * (d - a) / (b - a)
    out: dict[int, float] = {}
    t = None
    for d in range(days[0], days[-1] + 1):
        t = filled[d] if t is None else t + TREND_ALPHA * (filled[d] - t)
        out[d] = t
    return out


def trend_at(trend: dict[int, float], day: int) -> float | None:
    """Trend on the latest day at or before `day` (it holds flat after the last weigh-in)."""
    best = None
    for d in trend:
        if d <= day and (best is None or d > best):
            best = d
    return None if best is None else trend[best]


def weight_slope(weights: dict[int, float], first: int, last: int) -> float | None:
    """Least-squares slope (kg/day) of the daily weigh-ins between `first` and `last` inclusive, or None when there
    are fewer than MIN_WEIGHED_DAYS of them or they span less than MIN_WEIGH_SPAN days."""
    pts = [(d, kg) for d, kg in weights.items() if first <= d <= last]
    if len(pts) < MIN_WEIGHED_DAYS or max(d for d, _ in pts) - min(d for d, _ in pts) < MIN_WEIGH_SPAN:
        return None
    mx = sum(d for d, _ in pts) / len(pts)
    my = sum(kg for _, kg in pts) / len(pts)
    sxx = sum((d - mx) ** 2 for d, _ in pts)
    return sum((d - mx) * (kg - my) for d, kg in pts) / sxx


@dataclass(frozen=True)
class FuelProfile:
    male: bool
    age_y: float
    height_cm: float
    start_weight_kg: float
    activity: str              # key of PAL
    rate: float                # goal, fraction of body weight per week; negative = lose
    start_day: int
    check_in_weekday: int = 1  # Monday


def clamp_rate(rate: float) -> float:
    return max(-MAX_LOSS_RATE, min(MAX_GAIN_RATE, rate))


@dataclass(frozen=True)
class CheckIn:
    day: int
    trend_kg: float
    rest_kcal: float
    expenditure: float
    observed: float | None     # expenditure implied by the last WINDOW_DAYS, if there was enough data
    weekly_rate: float | None  # observed weight change, fraction of body weight per week
    kcal: float
    protein_g: float
    fat_g: float
    carb_g: float
    floor_applied: bool
    step_limited: bool


def macros(kcal: float, weight_kg: float) -> tuple[float, float, float]:
    protein = PROTEIN_G_PER_KG * weight_kg
    fat = FAT_SHARE * kcal / 9.0
    carb = max(0.0, kcal - 4.0 * protein - 9.0 * fat) / 4.0
    return protein, fat, carb


def fuel_check_ins(profile: FuelProfile, weigh_ins: Sequence[tuple[int, float]], intake: dict[int, float],
                   through_day: int) -> list[CheckIn]:
    """Targets at the start day and at every check-in weekday after it, up to `through_day`.
    `intake` holds the kcal eaten on each fully logged day; other days are absent."""
    weights = daily_weights(weigh_ins)
    trend = trend_series(weigh_ins)
    rate = clamp_rate(profile.rate)
    out: list[CheckIn] = []
    for c in range(profile.start_day, through_day + 1):
        if c != profile.start_day and weekday(c) != profile.check_in_weekday:
            continue
        w = trend_at(trend, c)
        w = profile.start_weight_kg if w is None else w
        ree = resting_energy(w, profile.height_cm, profile.age_y, profile.male)
        first, last = c - WINDOW_DAYS, c - 1
        slope = weight_slope(weights, first, last)
        weekly = None if slope is None else slope * 7.0 / w
        observed = None
        if not out:
            e = ree * PAL[profile.activity]
        else:
            e = out[-1].expenditure
            logged = [intake[d] for d in range(first, last + 1) if d in intake]
            if slope is not None and len(logged) >= MIN_LOGGED_DAYS:
                observed = sum(logged) / len(logged) - KCAL_PER_KG * slope
                e = e + BLEND * (observed - e)
        e = min(max(e, E_MIN_FACTOR * ree), E_MAX_FACTOR * ree)
        kcal = e + rate * w * KCAL_PER_KG / 7.0
        limited = False
        if out and kcal < out[-1].kcal - MAX_STEP_KCAL:
            kcal = out[-1].kcal - MAX_STEP_KCAL   # targets may rise at once but fall at most 200 kcal per check-in
            limited = True
        floor = kcal < ree
        kcal = max(kcal, ree)
        p, f, cb = macros(kcal, w)
        out.append(CheckIn(c, w, ree, e, observed, weekly, kcal, p, f, cb, floor, limited))
    return out


def targets_on(check_ins: Sequence[CheckIn], day: int) -> CheckIn | None:
    """The check-in in force on `day`: the latest one on or before it."""
    best = None
    for ci in check_ins:
        if ci.day <= day:
            best = ci
    return best


FAST_LOSS_RATE = -MAX_LOSS_RATE   # Helms et al. 2014 upper bound; faster than this triggers an "eat to target" order


def losing_too_fast(ci: CheckIn | None) -> bool:
    return ci is not None and ci.weekly_rate is not None and ci.weekly_rate < FAST_LOSS_RATE



# ---------------------------------------------------------------------------
# 8. Train (MODEL.md section 11)
# ---------------------------------------------------------------------------

E1RM_MAX_REPS = 12          # ASSUMPTION: no estimate above 12 reps


def e1rm(weight: float, reps: int) -> float | None:
    """Epley: w (1 + r/30) for r >= 2; a single is its own 1RM. None above E1RM_MAX_REPS or for no reps."""
    if reps <= 0 or reps > E1RM_MAX_REPS:
        return None
    return weight if reps == 1 else weight * (1.0 + reps / 30.0)


PLATE_STEP = {"barbell": 2.5, "dumbbell": 2.0, "cable": 2.5, "machine": 2.5, "body": 0.0}   # ASSUMPTION
UP_SMALL, UP_LARGE, DOWN = 0.025, 0.05, 0.10   # ASSUMPTIONS inside ACSM 2009's 2-10%


@dataclass(frozen=True)
class Prescription:
    sets: int
    lo: int
    hi: int
    large: bool          # lower-body main lift: bigger increase
    equipment: str       # key of PLATE_STEP


def _same_weight(session: Sequence[tuple[float, int]]) -> float | None:
    ws = {w for w, _ in session}
    return session[0][0] if len(ws) == 1 else None


def next_load(p: Prescription, history: Sequence[Sequence[tuple[float, int]]]) -> tuple[str, float | None]:
    """Double progression (ACSM 2009). history: working sets (weight, reps) per session, oldest first.
    Returns (action, weight): start / up / down / hold."""
    if not history:
        return "start", None
    last = history[-1]
    w = last[0][0]
    step = PLATE_STEP[p.equipment]
    if len(history) >= 2:
        a, b = history[-2], history[-1]
        wa, wb = _same_weight(a), _same_weight(b)
        if wa is not None and wa == wb and len(a) >= p.sets and len(b) >= p.sets:
            if all(r >= p.hi + 1 for _, r in a) and all(r >= p.hi + 1 for _, r in b):
                if step == 0:
                    return "hold", w
                target = w * (1.0 + (UP_LARGE if p.large else UP_SMALL))
                up = math.ceil(target / step - 1e-9) * step
                return "up", max(up, w + step)
            if all(sum(1 for _, r in s_ if r < p.lo) * 2 > len(s_) for s_ in (a, b)):
                if step == 0:
                    return "hold", w
                down = math.floor(w * (1.0 - DOWN) / step + 1e-9) * step
                return "down", max(down, 0.0)
    return "hold", w


PLAN_GROWTH = 1.08          # ASSUMPTION
RECOVERY_FACTOR = 0.7       # ASSUMPTION: every 4th week
TAPER_FACTORS = (0.55, 0.45)  # week before race, race week. Bosquet 2007: cut 41-60% over two weeks
CAP_FACTOR = 1.1            # ASSUMPTION: never above 110% of the biggest of the last 3 completed weeks
RESTART_FLOOR = 0.5         # ASSUMPTION: after a break the cap restarts at half of V0


def plan_base(i: int, v0: float, vp: float) -> float:
    n = i - (i + 1) // 4      # non-recovery weeks up to week i
    return min(vp, v0 * PLAN_GROWTH ** max(n, 0))


@dataclass(frozen=True)
class PlanWeek:
    index: int
    minutes: float
    phase: str              # BASE, BUILD, TAPER, AFTER
    recovery: bool
    capped: bool


def week_plan(i: int, v0: float, vp: float, race_week: int | None, actual: dict[int, float]) -> PlanWeek:
    """Planned swim+bike+run minutes for week i (0 = setup week)."""
    recovery = False
    if race_week is not None and i > race_week:
        m, phase = min(v0, 0.5 * plan_base(max(race_week - 2, 0), v0, vp)), "AFTER"
    elif race_week is not None and i >= race_week - 1:
        pre = plan_base(max(race_week - 2, 0), v0, vp)
        m, phase = pre * (TAPER_FACTORS[0] if i == race_week - 1 else TAPER_FACTORS[1]), "TAPER"
    else:
        recovery = i % 4 == 3
        m = plan_base(i, v0, vp) * (RECOVERY_FACTOR if recovery else 1.0)
        phase = "BASE" if race_week is None or race_week - i >= 13 else "BUILD"
    prior = [actual[j] for j in (i - 1, i - 2, i - 3) if j in actual]
    capped = False
    if prior:
        cap = max(CAP_FACTOR * max(prior), RESTART_FLOOR * v0)
        if m > cap:
            m, capped = cap, True
    return PlanWeek(i, m, phase, recovery, capped)


# The week (MODEL.md 11.3): (ISO day, sport, weight, base intensity, build intensity, build only)
WEEK_SLOTS = [
    (1, "SWIM", 1.0, "easy", "easy", False),
    (2, "RUN", 0.8, "easy", "easy", False),
    (4, "BIKE", 1.2, "quality", "quality", False),
    (5, "SWIM", 1.0, "easy", "quality", False),
    (6, "BIKE", 2.6, "easy", "easy", False),
    (6, "RUN", 0.4, "easy", "easy", True),     # brick run off the long ride
    (7, "RUN", 1.8, "easy", "easy", False),
]
MIN_SESSION = 15.0          # ASSUMPTION


def round5(x: float) -> float:
    return math.floor(x / 5.0 + 0.5) * 5.0


def week_sessions(week: PlanWeek) -> list[tuple[int, str, float, str]]:
    """(day, sport, minutes, intensity) for a planned week."""
    if week.minutes <= 0:
        return []
    build = week.phase in ("BUILD", "TAPER")
    slots = [s_ for s_ in WEEK_SLOTS if build or not s_[5]]
    total = sum(s_[2] for s_ in slots)
    out = []
    for day, sport, wgt, base_i, build_i, _ in slots:
        intensity = "easy" if week.recovery or week.phase == "AFTER" else (build_i if build else base_i)
        out.append((day, sport, max(MIN_SESSION, round5(week.minutes * wgt / total)), intensity))
    return out


def session_load(minutes: float, rpe: float) -> float:
    """Foster 2001: session RPE (0-10) x minutes."""
    return minutes * rpe


EASY_RPE = 4                # ASSUMPTION: a session rated 4 or below counts as easy


def easy_share(rpes: Sequence[float]) -> float | None:
    return None if not rpes else sum(1 for r in rpes if r <= EASY_RPE) / len(rpes)
