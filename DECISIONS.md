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
