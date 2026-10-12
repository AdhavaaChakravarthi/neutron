# Commander decisions

Every assumption or choice that isn't forced by a source goes here, with who decided. Open decisions block the phase named.

## Open (need your call)

None right now. The next module spec (see ARCHITECTURE.md phases) will raise its own.

## Decided

| ID | Decision | By | Basis |
|---|---|---|---|
| D-01 | Name: Commander (was Neutron); you follow its orders for 7 months | You | — |
| D-02 | Personal use only, sideloaded; no Play Store work ever | You | — |
| D-03 | Android only (Kotlin, Compose) | You (accepted the prompt) | — |
| D-04 | No wearable: phone-inferred sleep is the primary source, confirmed each morning | You | — |
| D-05 | Code lives in a new private GitHub repo `neutron`, pushed each phase | You | — |
| D-06 | Energy curve uses the three-process model with Ingre et al. 2014 defaults | Claude | MODEL.md §2 |
| D-07 | Omit the S "brake" in v1 because its exact form couldn't be read | Claude | MODEL.md §2 |
| D-08 | Process U phase reconstructed as trough 3 h before p | Claude | MODEL.md §2 |
| D-09 | Sleep need prior 8.0 h, prior weight 5 nights | Claude | MODEL.md §3 |
| D-10 | Qualifying nights for need: natural wake and debt < 2 h | Claude | MODEL.md §3 |
| D-11 | Debt: 14 nights, linear recency weights, surplus repays, floored at 0; bands 2 h / 5 h | Claude | MODEL.md §4 |
| D-12 | First-guess phase shift from habitual mid-sleep vs. 03:00, clamped 13–21 h | Claude | MODEL.md §5 |
| D-13 | Calibration after ≥ 20 KSS ratings over ≥ 7 days; fit p and Ua; adopt only if held-out error improves | Claude | MODEL.md §5 |
| D-14 | Detection window 18:00–15:00 (re-centred for late sleepers), 5-min bins, min rest 3 h | Claude | MODEL.md §6 |
| D-15 | Confirmed rest period counts as sleep; no latency subtracted | Claude | MODEL.md §6; user can edit |
| D-16 | Naps logged manually and counted toward that day's sleep | Claude | MODEL.md §6 |
| D-17 | Health Connect beats phone inference; manual edits beat both | Claude | SPEC edge cases |
| D-18 | Daily-order offsets: grogginess cut-off W > −1, peak band 0.5, 15 min to fall asleep, debt allowance 15 min (2–5 h) / 30 min (> 5 h), caffeine 8 h before lights out, light within 1 h, wind-down 60 min, 30 min/day schedule shift | Claude | MODEL.md §7 |
| D-19 | Re-entrainment after travel ≤ 1 h/day | Claude | Unsourced; source or revise before Phase 4 |
| D-20 | minSdk 34 | Claude | Health Connect is built into Android 14+ |
| D-21 | No `INTERNET` permission; local crash log; Auto Backup off for the database; JSON export instead | Claude | Personal health data stays on the phone |
| D-22 | Nudges via inexact `AlarmManager`, background compute via WorkManager | Claude | No exact-alarm permission needed |
| D-23 | Chart drawn with a custom Compose Canvas, no chart library | Claude | Full control over zones and accessibility, one less dependency |
| D-24 | Android builds run in GitHub Actions and on your machine, not the cloud workspace. `:model` and the order text are compiled and tested here with a local Kotlin compiler | Claude | Workspace can't reach Google Maven / Maven Central / Gradle |
| D-25 | Curve shape: keep the published three-process model; calibration against your ratings decides whether you have a dip (was OD-1, option A) | Claude's recommendation; you went ahead without changing it — reversible | MODEL.md §2 |
| D-26 | Sleep debt shown beside the curve, not baked into it; evaluate McCauley et al. 2009 later (was OD-2, option A) | Claude's recommendation; you went ahead without changing it — reversible | MODEL.md §2 |
| D-27 | Scope: everything from the Kodoma plan (sleep and energy, nutrition, training, mobility, gut log), one module at a time, each specced with sourced rules before code; rules-based, no AI | You | Kodoma plan |
| D-28 | One fixed signing key committed in `app/signing/` so every CI build installs over the last and keeps your data | Claude | Private repo, sideloaded personal app; a leaked key could only sign updates to this app |
| D-29 | Hilt deferred to Phase 3 (when `:data` arrives); Phase 2 uses `AndroidViewModel` + DataStore | Claude | Fewer moving parts in a build that can only be compiled in CI |
| D-30 | Library versions pinned to a set released together in Nov–Dec 2024 (AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, Gradle 8.10.2), compileSdk/targetSdk 35 | Claude | Can't check the newest versions from here; upgrade as one batch later |
| D-31 | Today's orders replace yesterday's 3 h before the usual wake time | Claude | MODEL.md §7 |
| D-32 | The 30 min/day limit applies to moving earlier; a later target wake applies at once | Claude | Moving later is easier for the body clock; source before relying on it |
| D-33 | DST days treated as normal 24 h wall-clock days until Phase 6 | Claude | Error is at most 1 h on two days a year |
| D-34 | Lint's Play Store target-SDK checks disabled | Claude | They don't apply to a sideloaded app |
| D-35 | Rules of command (SPEC.md) are product rules every module must follow | Claude | Following every order for 7 months only works if no order can hurt you |
| D-36 | Reminders use exact alarms via `USE_EXACT_ALARM` (granted at install), falling back to inexact if unavailable | Claude | Orders like lights out need the right minute; Play's restriction of this permission to alarm apps doesn't apply to a sideloaded app |
| D-37 | Dark-only tactical HUD look, after the reference image you chose; JetBrains Mono (SIL OFL, bundled with its license) for labels and numbers, system sans for sentences | You (reference) + Claude (details) | A dark screen also suits evening use |
| D-38 | Reminders on by default: up, sharpest window, last caffeine, wind down, lights out. Melatonin off (an estimate with no action of its own). Caffeine warning 30 min before the cutoff. Each can be toggled on Today or in Settings | Claude | Few enough a day that they stay worth reading |
| D-39 | Orders more than 20 min late (phone was off) are skipped, not shown | Claude | An old "wind down" at 2 a.m. would be noise |
| D-40 | Campaign = 7 calendar months from the start date (onboarding day, or first run of this version), editable in Settings | Claude | D-01 |
| D-41 | Commander is not an alarm clock: tomorrow's wake order has a "Set alarm" button that opens the clock app pre-filled | Claude | The clock app is more reliable for waking than a notification |
| D-42 | Reminders (planned for Phase 5) pulled forward ahead of Phase 3 at your request | You | — |
| D-43 | Scope: everything Rise does that works offline and phone-only, plus Fuel (MacroFactor-style), Train (strength, triathlon, mobility) and a gut log, shipped as four releases | You | Cut: barcode scanning, AI food logging, sleep sounds, calendar sync and a smart alarm (no internet, no wearable) |
| D-44 | 30-minute steps start from the earlier of the habitual (7-night median) time and last night's time | Claude | MODEL.md §9; without it, steps stalled for days in simulation |
| D-45 | Morning report 1 h after habitual wake; detected nights saved unconfirmed until you confirm or fix them | Claude | MODEL.md §9 |
| D-46 | No Hilt: one `AppContainer` built by `CommanderApplication` (replaces D-29) | Claude | Fewer moving parts; one user, a handful of screens |
| D-47 | Health Connect deferred until there's a device that writes sleep to it | Claude | You have no wearable; phone detection plus confirmation covers it |
| D-48 | Room for records (schemas exported to `app/schemas` so later releases migrate instead of wiping) | Claude | ARCHITECTURE.md |
| D-49 | Ticking orders off is informational: a count for today, no streaks | Claude | Rules of command 7 |
| D-50 | Alertness ratings (KSS 1–9) are collected now for curve calibration later | Claude | MODEL.md §5 |
| D-51 | Food data is USDA SR Legacy, shipped inside the app (7,448 foods, 220 KB). No branded foods or barcodes | Claude | Public domain and offline; branded databases need the internet (D-43 cut barcodes) |
| D-52 | Fuel's adaptive method is our own energy-balance loop (MODEL.md §10), not a copy of MacroFactor's | Claude | Their algorithm is proprietary; CLAUDE.md says don't copy methods |
| D-53 | Expenditure: 21-day window; needs 14 fully logged days and 8 weigh-ins spanning 14 days; each check-in moves halfway; kept between REE and 2.5 × REE | Claude | Tuned in simulation (`check_model.py`): converges within 150 kcal by week 8 from guesses 27% high or 10% low |
| D-54 | Weight change for expenditure comes from a least-squares line through raw weigh-ins, not from the trend | Claude | The 10% trend lags ~10 days and biased early estimates in simulation |
| D-55 | Calorie target never below resting energy; it may rise at once but falls at most 200 kcal per check-in; a goal you change applies at once | Claude | Rules of command 3 and 4 |
| D-56 | Protein 1.6 g/kg trend weight for every goal | Claude | Morton et al. 2018. Helms et al.'s higher lean-mass figures are for lean contest-prep athletes |
| D-57 | Fat 25% of calories; carbohydrate is the remainder, shown against the Burke et al. g/kg bands | Claude | Inside the IOM 20–35% range |
| D-58 | Tuesday fruit day: logged, counted in expenditure, no macro targets, no making up the difference on other days. Saturday vegan and everyday fish-only are search filters. All editable | Claude | Your weekly practice; Commander respects it rather than ordering around it |
| D-59 | Weekly check-in on Monday | Claude | Tuesday is a fruit day, so Monday's targets start on a normal day |
| D-60 | A day counts for expenditure when it has at least one entry and isn't marked "not fully logged" | Claude | Partly logged days would pull the estimate down |
| D-61 | Goal choices: lose 1, 0.5 or 0.25% a week, maintain, gain 0.25 or 0.5% a week; setup suggests lose 0.5% | Claude | Helms et al. 2014; the slower end protects triathlon training |
| D-62 | Daily weigh-in order after waking, on by default once Fuel is set up | Claude | The trend and expenditure need frequent weigh-ins |
