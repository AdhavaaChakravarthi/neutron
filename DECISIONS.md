# Neutron decisions

Every assumption or choice that isn't forced by a source goes here, with who decided. Open decisions block the phase named.

## Open (need your call)

### OD-1: Curve shape (blocks Phase 2)

The published three-process model gives one afternoon peak and no dip or evening second wind (MODEL.md §2).

| Option | What it means |
|---|---|
| **A. Keep the published model; let calibration decide (recommended)** | Default curve has one peak. The 12-hour component's amplitude is one of the two parameters fitted to your sleepiness ratings, so if you really do dip after lunch, your curve will show it. Honest, and personal. |
| B. Swap in a circadian term with a stronger 12-hour harmonic | e.g. the SAFTE model's circadian process. Untested here; would need its own sourcing and checks before adoption. |
| C. Tune it to look like Rise | Not recommended: it would show a dip the evidence doesn't predict for you. |

### OD-2: Should sleep debt change the curve? (blocks Phase 2)

The model barely reacts to several short nights in a row, while the research says the cost accumulates (MODEL.md §2).

| Option | What it means |
|---|---|
| **A. v1 shows debt separately; curve stays uncorrected; evaluate McCauley et al. 2009 for v1.1 (recommended)** | Today shows the curve and the debt side by side, and the "Why this curve?" page says the curve reflects mostly last night. Ships sooner with nothing invented. |
| B. Adopt a chronic-restriction model now | McCauley et al. 2009 or Rajdev et al. 2013 (both in MODEL.md sources). More faithful, but adds a phase of sourcing parameters and validating. |
| C. Subtract a debt-based offset from the curve | Simple, but the size of the offset would be made up. Not recommended. |

## Decided

| ID | Decision | By | Basis |
|---|---|---|---|
| D-01 | Name: Neutron | You | — |
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
| D-18 | Daily-plan offsets (grogginess cut-off, peak band, 15 min latency, 30 min debt extension, 8 h caffeine, 1 h light, 60 min wind-down, 30 min/night shift) | Claude | MODEL.md §7; all user-adjustable where it matters |
| D-19 | Re-entrainment after travel ≤ 1 h/day | Claude | Unsourced; source or revise before Phase 4 |
| D-20 | minSdk 34 | Claude | Health Connect is built into Android 14+ |
| D-21 | No `INTERNET` permission; local crash log; Auto Backup off for the database; JSON export instead | Claude | Personal health data stays on the phone |
| D-22 | Nudges via inexact `AlarmManager`, background compute via WorkManager | Claude | No exact-alarm permission needed |
| D-23 | Chart drawn with a custom Compose Canvas, no chart library | Claude | Full control over zones and accessibility, one less dependency |
| D-24 | Android builds run in GitHub Actions and on your machine, not the cloud workspace | Claude | Workspace can't reach Google Maven / Maven Central / Gradle |
