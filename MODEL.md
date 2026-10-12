# Commander model

How Commander turns sleep history into a sleep-need estimate, a sleep-debt number and a daily energy curve. Every parameter has a source or is labelled **ASSUMPTION** or **RECONSTRUCTED**. The executable version is `tools/model-reference/commander_model.py`; `check_model.py` runs the behavioural checks below.

Commander is a personal wellness tool. Nothing here diagnoses or treats a sleep disorder.

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

- **No afternoon dip or evening second wind.** The 12-hour component is too small to beat the rising circadian term, so the curve flattens around 11:30–14:00 but never turns down. A Rise-style "two peaks and a dip" curve is not what this model predicts with its published defaults. → **D-25**: keep the published curve; your sleepiness ratings decide whether you have a dip.
- **Short nights barely accumulate.** Five nights of 6 h lower the mean daytime value only from 11.60 to 11.33, and day 5 looks like day 1. One 9 h night fully restores it. This is a known weakness of two-process-type models: they don't predict the effects of chronic restriction [14, 15], which Van Dongen et al. showed are cumulative and dose-dependent over 14 days [5], and which one recovery night does not reverse [6]. → **D-26**: debt is shown beside the curve, not baked into it; a chronic-restriction model is evaluated for a later version.

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

**Circadian phase p.** Default p = 16.8 h [3]. For a first guess, Commander shifts p by how far the user's habitual mid-sleep is from 03:00 (**ASSUMPTION**: the default is treated as belonging to a ~23:00–07:00 sleeper), clamped to 13–21 h. Ingre et al. found phase does vary with self-rated chronotype, from 14.61 h for the most morning-type group to 16.62 h for the most evening-type [3], so a personal p is justified; the mid-sleep mapping is not.

**Calibration (replaces the guess).** After at least 20 KSS ratings over at least 7 days, fit p and Ua by least squares against `KSS = 10.6 − 0.6·A`, regularised toward the defaults, and adopt the fit only if it lowers error on held-out ratings (**ASSUMPTION** for the thresholds). Caveat: in Van Dongen et al., subjective sleepiness levelled off while performance kept falling [5], so a curve fitted to how you feel can understate accumulated debt. That is one more reason debt is shown separately.

## 6. Phone-only sleep detection

Without a wearable, Commander infers a **rest period** (phone down → phone up) from screen events and asks the user to confirm it each morning.

- **Signal.** Screen-on events from `UsageStatsManager` (`UsageEvents.Event.SCREEN_INTERACTIVE`), binned into 5-minute counts over a window from 18:00 to 15:00 next day, re-centred on habitual mid-sleep for late sleepers (**ASSUMPTION**). Phase 3 must check on your phone whether notifications that light the screen inflate the count; if so, switch to unlock events (`KEYGUARD_HIDDEN`).
- **Model.** Two Poisson rates, low inside the rest period and high outside; choose the start and end bins that maximise the likelihood, minimum 3 h. This follows SensibleSleep (Cuttone et al.), which used screen on/off events, tolerates the odd night-time phone check, and reported 0.89 accuracy against an activity-tracker armband [13]. On a synthetic night with a 3 a.m. check, the reference detector is within 5 minutes at both ends.
- **Limits.** Screen-off is rest, not sleep [13]. Lying awake, reading a book, or a phone left charging in another room all count as "asleep". The morning confirmation is the correction, so a confirmed night is the source of truth.
- **Health Connect.** If any app has written a `SleepSessionRecord` for that night, Commander uses it instead of inferring, and still asks for confirmation. I could not confirm that a phone without a wearable writes sleep sessions to Health Connect, so Commander does not rely on it.
- **Naps** are logged manually in v1. They feed Process S and count toward that day's sleep total (**ASSUMPTION**).

## 7. Daily orders derived from the curve

Implemented in `plan_day` / `plan_for_schedule` (Python) and `planDay` / `planForSchedule` (Kotlin).

| Order | Rule | Basis |
|---|---|---|
| Up, then outside light | Light within 1 h of waking | Light after the circadian low advances the clock, light before it delays it [12]; the 1 h window is an **ASSUMPTION** |
| Groggy | From waking until W > −1: ln(5.72)/1.51 ≈ 69 min | Process W [3]; the cut-off of 1 unit is an **ASSUMPTION** |
| Sharpest (estimate) | Contiguous stretch around the day's maximum where A is within 0.5 of it, searched between the end of grogginess and lights out | **ASSUMPTION**. With default parameters this is wide (≈ 12:35–19:20 for a 23:30–07:30 sleeper) because the curve is nearly flat; shown with its peak time and labelled an estimate |
| Dip (estimate) | Lowest strict local minimum in the same window, if any | §2; with default parameters there is none |
| Sleepiness sets in (estimate) | First time after the peak that A < 7, if before wind-down | Sleepiness threshold [2] |
| Next wake | Moves toward the target wake time, but at most 30 min earlier than the usual wake per day; a later target applies at once | Rule of command; 30 min is an **ASSUMPTION** |
| Lights out | Next wake − need − 15 min to fall asleep − debt allowance (15 min if debt 2–5 h, 30 min if > 5 h), but never more than 30 min earlier than habitual sleep onset | Need §3. Moving bedtime earlier is limited because sleep is hardest to start in the 2–3 h before habitual bedtime, the "forbidden zone" [10]. 15 min, the debt allowances and 30 min are **ASSUMPTIONS** |
| Last caffeine | 8 h before lights out | 400 mg 6 h before bed still cut sleep by over an hour [11], so the cutoff must be earlier than 6 h; 8 h is an **ASSUMPTION** |
| Melatonin rising (estimate) | Habitual sleep onset − 2 h | Sleep onset averages ≈ 2 h after melatonin onset, but the spread across people was 2.4 h in early sleepers and 4.5 h in late sleepers [9] |
| Wind down | 60 min before lights out; dim lights, screens away | Evening light delays the clock [12]; 60 min is an **ASSUMPTION** |

**Before real nights exist** (until Phase 3), the history is 20 identical nights of the user's stated usual schedule, debt is 0, habitual onset is the stated bedtime, and p is the mid-sleep first guess from §5. Because "usual" doesn't update on its own yet, the 30-minute steps repeat the first step each day until the user updates their usual times or detection arrives.

**Which day's orders.** Today's orders replace yesterday's 3 h before the usual wake time, so at 01:00 the screen still shows last night's lights out (**ASSUMPTION**).

**Travel and DST.** p is kept in body time. After a time-zone change it re-entrains toward the new local time at no more than 1 h per day (**ASSUMPTION**, to be sourced before Phase 4). DST nights are 23 or 25 h long and must not create or remove sleep.

## 8. What the checks cover

`python3 tools/model-reference/check_model.py` currently reports:

- **Pass (13):** steady state reached; groggy on waking; single peak near acrophase; noon KSS in "alert" range; restriction lowers alertness; late sleeper's curve shifts by the expected 3 h; four debt checks; two need checks; detection within 15 min despite a night-time check.
- **Known limitations (3, reported every run, don't fail the build):** no afternoon dip/second wind; no accumulation across short nights; one recovery night fully restores. Decided in D-25 and D-26.

`python3 tools/model-reference/make_golden.py` writes golden vectors (curves for three schedules, debt, need, acrophase, detector, daily orders for six schedules). The Kotlin `:model` tests must match them to 1e-6, and CI runs `make_golden.py --check` so the goldens can't drift from the reference. A deliberate change to an inertia constant or the debt allowance makes the Kotlin tests fail, so they do bite.

## 9. Real nights

Implemented in the "Real nights" section of `commander_model.py` and in `model/.../History.kt`; golden vectors in `golden/history*.csv`.

**Which nights.** One night per wake day: a recorded night if there is one (the longest, if several), otherwise a provisional night on your habitual schedule. Naps that overlap a night are ignored. With no recorded nights the plan is identical to the schedule-only plan in §7 (tested).

**Habitual times.** Median sleep onset and median wake of the last 7 recorded nights, with onsets compared as hours after noon so 23:30 and 00:30 sit together. Fewer than 3 recorded nights: your stated usual schedule (**ASSUMPTION** for 7 and 3).

**Where the 30-minute steps start.** The earlier of the habitual time and last night's time, for both bedtime and wake. Following tonight's order moves tomorrow's plan on by another step. One late night can't drag the plan later, because the median stays put (**ASSUMPTION**, D-44). Without this, the steps stalled for days in simulation, because one earlier night barely moves a 7-night median.

**Daily sleep and debt.** Total sleep per wake day: nights plus naps that end that day. A "didn't sleep" mark counts as 0. Days with no data are left out, not counted as 0. Debt is the §4 formula over the 14 most recent days with data.

**Sleep need from history.** A night qualifies when you woke without an alarm and the debt before it (against the 8 h prior) was under 2 h. The estimate is the §3 shrinkage toward 8 h. You can switch to a manual value in Settings.

**Sleep Regularity Index.** 200 × P(same state 24 h apart) − 100, compared across consecutive recorded wake days in noon-to-noon windows at 5-minute resolution. Needs at least 3 such pairs. This follows the definition in Phillips et al. 2017 [17]: the chance of being in the same state (asleep or awake) 24 h apart, scaled so a perfectly regular sleeper scores 100 and a random one 0. The scaling formula is written from that verified definition; I could not read the paper's equation directly.

**Social jetlag.** |median mid-sleep before free days − median mid-sleep before workdays| (Wittmann et al. 2006 [18]). A free night is one that ends on a free day (Saturday and Sunday by default, editable). Needs 2 free nights and 3 work nights.

**Chronotype (MSFsc).** Mid-sleep on free days, corrected for catch-up sleep: MSFsc = MSF if SDf ≤ SDw, else MSF − (SDf − SDweek)/2, where SDweek = (SDw·WD + SDf·FD)/7 [19]. Not available when most free nights ended with an alarm, matching the MCTQ rule.

**Phone detection in practice.** The morning report fires 1 h after habitual wake (**ASSUMPTION**). It reads screen-on times in the §6 window, runs the detector and saves the result as unconfirmed. Your confirmation (or edit) is final. Detection is skipped when the rest period touches the end of the available data, so a lie-in isn't cut short.

**Nap order.** Shown when debt is at least 2 h: up to 20 minutes, ending by the caffeine cutoff. A 10-minute nap was most effective and naps up to 30 min still helped after restricted sleep [20]. The 20-minute cap and the cutoff are **ASSUMPTIONS**.

**Exercise timing.** The wind-down order adds "no hard training from now". In a meta-analysis evening exercise did not harm sleep overall, but vigorous exercise ending ≤ 1 h before bedtime might [21].

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
17. Phillips AJK, Clerx WM, O'Brien CS, et al. Irregular sleep/wake patterns are associated with poorer academic performance and delayed circadian and sleep/wake timing. *Scientific Reports*. 2017;7:3216. doi:10.1038/s41598-017-03171-4 † (definition checked via two papers that cite it; the article number and DOI are from memory)
18. Wittmann M, Dinich J, Merrow M, Roenneberg T. Social jetlag: misalignment of biological and social time. *Chronobiology International*. 2006;23(1–2):497–509. † (the definition matches the MCTQ documentation; bibliographic detail from memory)
19. Roenneberg T, Wirz-Justice A, Merrow M. Life between clocks: daily temporal patterns of human chronotypes. *Journal of Biological Rhythms*. 2003;18(1):80–90; and Roenneberg T, Allebrandt KV, Merrow M, Vetter C. Social jetlag and obesity. *Current Biology*. 2012;22(10):939–943. (Formula as given in the `mctq` R package documentation.)
20. Brooks A, Lack L. A brief afternoon nap following nocturnal sleep restriction: which nap duration is most recuperative? *Sleep*. 2006;29(6):831–840. ‡ (finding checked via a secondary summary; citation details from memory)
21. Stutz J, Eiholzer R, Spengler CM. Effects of evening exercise on sleep in healthy participants: a systematic review and meta-analysis. *Sports Medicine*. 2019;49(2):269–287. doi:10.1007/s40279-018-1015-0 (abstract checked; author names from memory)

