# Neutron model

How Neutron turns sleep history into a sleep-need estimate, a sleep-debt number and a daily energy curve. Every parameter has a source or is labelled **ASSUMPTION** or **RECONSTRUCTED**. The executable version is `tools/model-reference/neutron_model.py`; `check_model.py` runs the behavioural checks below.

Neutron is a personal wellness tool. Nothing here diagnoses or treats a sleep disorder.

---

## 1. Inputs

| Input | Where it comes from |
|---|---|
| Sleep episodes (start, end, nap or night) | Phone-inferred, Health Connect, or manual (§6) |
| "Woke without an alarm?" per night | Morning check-in |
| Sleepiness ratings, KSS 1–9 | Optional check-ins (§5) |
| Target wake time | Settings |

All times are stored as instants plus UTC offset. Clock hours are computed in the zone the user was in at that instant.

## 2. Energy curve: three-process model of alertness

Predicted alertness `A(t) = S + C + U + W`, the three-process model (TPM) of Åkerstedt and Folkard, which extends Borbély's two-process model of sleep regulation [1]. Parameters are the defaults reported by Ingre et al. 2014 [3] unless noted.

**Process S, homeostatic** (t in hours)

- Awake: `S(t) = la + (S_wake − la)·e^(d·t)`, la = 2.4, d = −0.0353
- Asleep: `S(t) = ha − (ha − S_sleep)·e^(g·t)`, ha = 14.3, g = ln((14.3 − 14.0)/(14.3 − 7.96))/8 ≈ −0.3813, i.e. S goes from 7.96 to 14.0 over 8 h of sleep [3]
- Ingre et al. add a "brake" that slows recovery above S = 12.2 [3]. Its exact form is in an equation image I could not read as text, so v1 **omits it**. Effect: recovery late in a long night is slightly faster than the published model.

**Process C, circadian**: `C(t) = Ca·cos(2π(t − p)/24)`, Ca = 2.5, p = 16.8 h (clock time of peak) [3].

**Process U, 12-hour component**: `U(t) = Um − Ua·cos(2π(t − (p − 3))/12)`, Ua = 0.5, Um = −0.5 [3]. **RECONSTRUCTED:** the phase (trough 3 h before p, so 13:48 by default) comes from the text of Åkerstedt, Folkard and Portin 2004 [2]; the equation itself is an image in both papers.

**Process W, sleep inertia**: `W(t) = Wc·e^(Wd·t)`, Wc = −5.72, Wd = −1.51 per hour since waking [3].

**Scale.** In the 2004 description, about 3 is extreme sleepiness, 7 is the sleepiness threshold (slow eye movements appear) and 14 is high alertness [2]. Ingre et al. map alertness to the Karolinska Sleepiness Scale as `KSS = 10.6 − 0.6·A` [3]; KSS is the 1–9 scale of Åkerstedt and Gillberg [4].

### What the published model predicts

![Energy curves](docs/img/energy-curves.png)

With default parameters, a steady 23:30–07:30 sleeper wakes at A ≈ 6.4 (below the threshold), is past grogginess within about an hour, rises to a **single peak at about 16:35**, and falls back to ~8 at bedtime. KSS at noon ≈ 3.2 ("alert").

Two things matter for the product:

- **No afternoon dip or evening second wind.** The 12-hour component is too small to beat the rising circadian term, so the curve flattens around 11:30–14:00 but never turns down. A Rise-style "two peaks and a dip" curve is not what this model predicts with its published defaults. → open decision **OD-1** in DECISIONS.md.
- **Short nights barely accumulate.** Five nights of 6 h lower the mean daytime value only from 11.60 to 11.33, and day 5 looks like day 1. One 9 h night fully restores it. This is a known weakness of two-process-type models: they don't predict the effects of chronic restriction [14, 15], which Van Dongen et al. showed are cumulative and dose-dependent over 14 days [5], and which one recovery night does not reverse [6]. → open decision **OD-2**.

## 3. Sleep need

**Prior: 8.0 h.** The AASM/SRS consensus is that adults 18–60 should regularly sleep 7 h or more, with individual need varying [8]. In 15 young men given 12 h in bed for 9 nights, the estimated optimal duration was 8.41 h (range 7.29–9.26), about an hour more than their habitual sleep [7]. 8.0 sits inside that evidence; it is a starting point, not a measurement.

**Personal estimate.** A night qualifies if the user woke without an alarm **and** debt before that night was under 2 h (**ASSUMPTION**: after a big debt, natural sleep runs long because of rebound [7]). Need = `(5 × prior + Σ qualifying TST) / (5 + n)`. The prior counts as 5 nights of evidence (**ASSUMPTION**). The user can override need in settings.

## 4. Sleep debt

Debt = `max(0, Σ_{k=0..13} w_k·(need − TST_k))`, with linear recency weights `w_k = (14 − k)/14` (last night = 1.0).

- 14 nights matches the protocol over which Van Dongen et al. saw deficits keep accumulating [5]. The linear weights, the 0 floor and letting surplus nights pay debt down are **product decisions**, not physiology. Banks et al. found one long night does not erase several short ones [6], which recency weighting only roughly mimics.
- Nights with no data are skipped, not treated as 0 h.
- Debt is shown as hours with a band: low < 2 h, moderate 2–5 h, high > 5 h (**ASSUMPTION**: bands are for readability only).

Checks that pass: 14 × 8 h at need 8 h → 0; 14 × 7 h → 7.5 h; a short night last night outweighs the same short night two weeks ago; 14 × 10 h → 0.

## 5. Personalisation

**Circadian phase p.** Default p = 16.8 h [3]. For a first guess, Neutron shifts p by how far the user's habitual mid-sleep is from 03:00 (**ASSUMPTION**: the default is treated as belonging to a ~23:00–07:00 sleeper), clamped to 13–21 h. Ingre et al. found phase does vary with self-rated chronotype, from 14.61 h for the most morning-type group to 16.62 h for the most evening-type [3], so a personal p is justified; the mid-sleep mapping is not.

**Calibration (replaces the guess).** After at least 20 KSS ratings over at least 7 days, fit p and Ua by least squares against `KSS = 10.6 − 0.6·A`, regularised toward the defaults, and adopt the fit only if it lowers error on held-out ratings (**ASSUMPTION** for the thresholds). Caveat: in Van Dongen et al., subjective sleepiness levelled off while performance kept falling [5], so a curve fitted to how you feel can understate accumulated debt. That is one more reason debt is shown separately.

## 6. Phone-only sleep detection

Without a wearable, Neutron infers a **rest period** (phone down → phone up) from screen events and asks the user to confirm it each morning.

- **Signal.** Screen-on events from `UsageStatsManager` (`UsageEvents.Event.SCREEN_INTERACTIVE`), binned into 5-minute counts over a window from 18:00 to 15:00 next day, re-centred on habitual mid-sleep for late sleepers (**ASSUMPTION**). Phase 3 must check on your phone whether notifications that light the screen inflate the count; if so, switch to unlock events (`KEYGUARD_HIDDEN`).
- **Model.** Two Poisson rates, low inside the rest period and high outside; choose the start and end bins that maximise the likelihood, minimum 3 h. This follows SensibleSleep (Cuttone et al.), which used screen on/off events, tolerates the odd night-time phone check, and reported 0.89 accuracy against an activity-tracker armband [13]. On a synthetic night with a 3 a.m. check, the reference detector is within 5 minutes at both ends.
- **Limits.** Screen-off is rest, not sleep [13]. Lying awake, reading a book, or a phone left charging in another room all count as "asleep". The morning confirmation is the correction, so a confirmed night is the source of truth.
- **Health Connect.** If any app has written a `SleepSessionRecord` for that night, Neutron uses it instead of inferring, and still asks for confirmation. I could not confirm that a phone without a wearable writes sleep sessions to Health Connect, so Neutron does not rely on it.
- **Naps** are logged manually in v1. They feed Process S and count toward that day's sleep total (**ASSUMPTION**).

## 7. Daily plan derived from the curve

| Item | Rule | Basis |
|---|---|---|
| Grogginess | Waking until W > −1 (≈ 69 min) | Process W [3]; the cut-off of 1 unit is an **ASSUMPTION** |
| Peak window | Where A is within 0.5 of the day's maximum | **ASSUMPTION** |
| Dip | Shown only if the (calibrated) curve has a real local minimum | §2 |
| Sleepy | A < 7 | Sleepiness threshold [2] |
| Recommended bedtime | Target wake − need − 15 min latency allowance, plus up to 30 min extra when debt is high | Need §3; 15 and 30 min are **ASSUMPTIONS** |
| Earliest sensible bedtime | Warn if the recommendation is > 1 h before habitual sleep onset; suggest moving 30 min a night | Sleep is hardest in the 2–3 h before habitual bedtime, the "forbidden zone" [10]; the 1 h and 30 min figures are **ASSUMPTIONS** |
| Melatonin rise (est.) | Habitual sleep onset − 2 h, shown with a wide margin | Sleep onset averages ≈ 2 h after melatonin onset, but the spread across people was 2.4 h in early sleepers and 4.5 h in late sleepers [9] |
| Caffeine cutoff | 8 h before recommended bedtime, adjustable | 400 mg 6 h before bed still cut sleep by over an hour [11], so the cutoff must be earlier than 6 h; 8 h is an **ASSUMPTION** |
| Morning light | Outdoor light within an hour of waking | Light after the circadian low advances the clock, light before it delays it [12]; the 1 h window is an **ASSUMPTION** |
| Wind-down | Start 60 min before recommended bedtime; dim lights | Evening light delays the clock [12]; 60 min is an **ASSUMPTION** |

**Travel and DST.** p is kept in body time. After a time-zone change it re-entrains toward the new local time at no more than 1 h per day (**ASSUMPTION**, to be sourced before Phase 4). DST nights are 23 or 25 h long and must not create or remove sleep.

## 8. What the checks cover

`python3 tools/model-reference/check_model.py` currently reports:

- **Pass (13):** steady state reached; groggy on waking; single peak near acrophase; noon KSS in "alert" range; restriction lowers alertness; late sleeper's curve shifts by the expected 3 h; four debt checks; two need checks; detection within 15 min despite a night-time check.
- **Known limitations (3, reported every run, don't fail the build):** no afternoon dip/second wind; no accumulation across short nights; one recovery night fully restores. These are OD-1 and OD-2.

Phase 2 generates golden vectors from this reference and the Kotlin `:model` module must match them to 1e-6.

---

## Sources

Retrieved and checked during Phase 1 unless marked. † = the paper and its findings were checked, but some bibliographic detail (usually the DOI or page range) is from memory. ‡ = only the title and authors were checked; the finding cited is from memory. Verify marked items before quoting them elsewhere.

1. Borbély AA. A two process model of sleep regulation. *Human Neurobiology*. 1982;1:195–204. (Cited from the reference list of [2].)
2. Åkerstedt T, Folkard S, Portin C. Predictions from the three-process model of alertness. *Aviation, Space, and Environmental Medicine*. 2004;75(3 Suppl):A75–A83.
3. Ingre M, Van Leeuwen W, Klemets T, Ullvetter C, Hough S, Kecklund G, Karlsson D, Åkerstedt T. Validating and extending the three process model of alertness in airline operations. *PLOS ONE*. 2014;9(10):e108679. doi:10.1371/journal.pone.0108679
4. Åkerstedt T, Gillberg M. Subjective and objective sleepiness in the active individual. *International Journal of Neuroscience*. 1990;52:29–37.
5. Van Dongen HPA, Maislin G, Mullington JM, Dinges DF. The cumulative cost of additional wakefulness: dose-response effects on neurobehavioral functions and sleep physiology from chronic sleep restriction and total sleep deprivation. *Sleep*. 2003;26(2):117–126. doi:10.1093/sleep/26.2.117 †
6. Banks S, Van Dongen HPA, Maislin G, Dinges DF. Neurobehavioral dynamics following chronic sleep restriction: dose-response effects of one night for recovery. *Sleep*. 2010;33(8):1013–1026. doi:10.1093/sleep/33.8.1013 †
7. Kitamura S, Katayose Y, Nakazaki K, et al. Estimating individual optimal sleep duration and potential sleep debt. *Scientific Reports*. 2016;6:35812. doi:10.1038/srep35812
8. Watson NF, Badr MS, Belenky G, et al. Recommended amount of sleep for a healthy adult: a joint consensus statement of the American Academy of Sleep Medicine and Sleep Research Society. *Journal of Clinical Sleep Medicine*. 2015;11(6):591–592. doi:10.5664/jcsm.4758
9. Sletten TL, Vincenzi S, Redman JR, Lockley SW, Rajaratnam SMW. Timing of sleep and its relationship with the endogenous melatonin rhythm. *Frontiers in Neurology*. 2010;1:137. doi:10.3389/fneur.2010.00137
10. Monterastelli AJ, Adams J, Eastman CI, Crowley SJ. The forbidden zone for sleep is more robust in adolescents compared to adults. *Frontiers in Sleep*. 2024;2:1304647. doi:10.3389/frsle.2023.1304647
11. Drake C, Roehrs T, Shambroom J, Roth T. Caffeine effects on sleep taken 0, 3, or 6 hours before going to bed. *Journal of Clinical Sleep Medicine*. 2013;9(11):1195–1200. doi:10.5664/jcsm.3170 †
12. Khalsa SBS, Jewett ME, Cajochen C, Czeisler CA. A phase response curve to single bright light pulses in human subjects. *Journal of Physiology*. 2003;549(Pt 3):945–952. doi:10.1113/jphysiol.2003.040477 ‡
13. Cuttone A, Bækgaard P, Sekara V, Jonsson H, Larsen JE, Lehmann S. SensibleSleep: a Bayesian model for learning sleep patterns from smartphone events. *PLOS ONE*. 2017;12(1):e0169901. doi:10.1371/journal.pone.0169901 †
14. McCauley P, Kalachev LV, Smith AD, Belenky G, Dinges DF, Van Dongen HPA. A new mathematical model for the homeostatic effects of sleep loss on neurobehavioral performance. *Journal of Theoretical Biology*. 2009;256(2):227–239. doi:10.1016/j.jtbi.2008.09.012
15. Rajdev P, Thorsley D, Rajaraman S, Rupp TL, Wesensten NJ, Balkin TJ, Reifman J. A unified mathematical model to quantify performance impairment for both chronic sleep restriction and total sleep deprivation. *Journal of Theoretical Biology*. 2013;331:66–77. doi:10.1016/j.jtbi.2013.04.013
