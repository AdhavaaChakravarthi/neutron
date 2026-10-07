"""Neutron reference model (Python).

This is the executable version of MODEL.md. It exists so the Kotlin port in
Phase 2 can be checked against golden vectors produced here. Keep the two in
sync: if a parameter or equation changes, change MODEL.md, this file, and the
Kotlin module together.

Sources for every parameter are in MODEL.md. Values marked RECONSTRUCTED or
ASSUMPTION there are flagged the same way here.
"""
from __future__ import annotations

import math
from dataclasses import dataclass
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
