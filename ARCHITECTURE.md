# Commander architecture

## Platform

- Android only, Kotlin, Jetpack Compose (Material 3).
- **minSdk 34** (Android 14), where Health Connect is part of the platform. compileSdk/targetSdk 35. Library versions are pinned in `gradle/libs.versions.toml` (D-30).
- Package and application id `com.adhavaa.commander`. Every build is signed with the same key (D-28) and numbered by the CI run, so each new APK installs over the last.
- Single user, sideloaded. No `INTERNET` permission in the manifest.

## Modules

```
neutron/
├── model/        pure Kotlin/JVM, no Android dependencies (one package per module: sleep now, training etc. later)
├── data/         Android library: storage and signal sources (Phase 3)
├── app/          Compose UI, ViewModels, order text, background work, notifications
└── tools/model-reference/   Python reference model + checks (golden-vector source)
```

| Module | Owns | Depends on |
|---|---|---|
| `:model` | Three-process curve, sleep need, sleep debt, rest-period detector, daily plan (zones, bedtime, nudge times), calibration fit | nothing (Kotlin stdlib, `java.time`) |
| `:data` | Room database, DataStore settings, `UsageStatsManager` screen-event source, Health Connect source, repositories, JSON export/import | `:model` |
| `:app` | Screens (`ui/`, HUD components in `Hud.kt`), ViewModel, order and reminder wording (`orders/`, pure Kotlin and unit-tested), notifications and exact alarms (`notify/`), Hilt wiring from Phase 3 (D-29), crash log | `:data`, `:model` |

`:model` being pure JVM is the point: every number the user sees is computed there and tested without an emulator.

## Key types in `:model`

```kotlin
data class SleepEpisode(val start: Instant, val end: Instant, val kind: Kind /* NIGHT, NAP */)
data class TpmParams(/* defaults from MODEL.md §2 */)
fun energyCurve(episodes: List<SleepEpisode>, from: Instant, to: Instant, zone: TimeZoneHistory, p: TpmParams): List<CurvePoint>
fun sleepDebt(nightsNewestFirst: List<Duration>, need: Duration): Duration
fun estimateNeed(qualifyingNights: List<Duration>, prior: Duration = 8.hours): Duration
fun detectRestPeriod(screenOnCounts: IntArray, binMinutes: Int, minRest: Duration = 3.hours): IntRange?
fun dailyPlan(curve: List<CurvePoint>, need: Duration, debt: Duration, targetWake: LocalTime, habits: HabitSettings): DailyPlan
fun calibrate(ratings: List<KssRating>, episodes: List<SleepEpisode>, prior: TpmParams): CalibrationResult
```

## Data model (Room)

| Table | Columns |
|---|---|
| `sleep_episode` | id, start (epoch ms), start_offset, end, end_offset, kind, source (PHONE / HEALTH_CONNECT / MANUAL / PROVISIONAL), confirmed, woke_naturally (nullable), created_at, updated_at |
| `night_signal` | night_date, bin_minutes, window_start, counts (blob), detector_result. Kept so detection is reproducible after the system drops old usage events |
| `kss_rating` | id, at, offset, value (1–9) |
| `calibration` | id, fitted_at, p, ua, n_ratings, heldout_error_default, heldout_error_fit, active |
| `zone_change` | at, from_offset, to_offset (travel history for re-entrainment) |

DataStore holds settings: schedule, target wake, need override, habit toggles and offsets, onboarding state.

Migrations are written and tested from the first schema; the database is never destructively rebuilt.

## Background work

| Job | Mechanism | When |
|---|---|---|
| Night detection | WorkManager periodic job | Hourly between habitual wake and +4 h until the night is resolved |
| Morning check-in | Notification posted by the detection job | When a rest period is found, or at habitual wake + 2 h if not |
| Daily plan | Recomputed in-process | On confirmation, edit, settings change, zone change |
| Reminders | `AlarmManager.setExactAndAllowWhileIdle` with `USE_EXACT_ALARM` (D-36), one alarm per upcoming reminder over the next two plan days, max 16 | Rebuilt on settings change, each fired order, boot, clock/zone change, app update |
| Reschedule | `RescheduleReceiver`: `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` | On reboot, update, manual clock or zone change |

## Privacy and failure handling

- No network permission, so no analytics and no crash upload. Uncaught exceptions are appended to a local rotating log viewable in Settings.
- Android Auto Backup is **off** for the database; the JSON export is the backup.
- Every source can be missing: no usage access, no Health Connect, no notifications. Each degrades to manual entry instead of crashing.

## Testing and CI

| Layer | How |
|---|---|
| `:model` | JUnit tests against golden vectors from `tools/model-reference` (curves, debt, need, acrophase, detector, daily orders; tolerance 1e-6), plus behaviour tests of the orders and rules of command |
| `:data` | Room DAO and migration tests; repositories with fake sources |
| `:app` | Pure tests for order text, reminders, brief and campaign; Robolectric tests that alarms are set and a fired order becomes a notification; render tests that draw every screen to PNG (attached to each release) and fail if blank |
| CI (GitHub Actions) | On every push: Python reference checks, golden freshness (`make_golden.py --check`), `:model:test`, `:app:testReleaseUnitTest`, `:app:lintRelease`, `:app:assembleRelease`, a check that the APK has no INTERNET permission, then a GitHub release with the APK on `main`. Emulator tests run locally, not in CI |

**Where things get built.** The cloud workspace these sessions run in can't reach Google's Maven repository, Maven Central or the Gradle distribution servers, so Android builds and tests run in GitHub Actions and on your machine. Each phase is pushed and is only called done once CI is green.

## Phases

Each phase ends with tests run, a pass/fail report, known gaps listed, and a stop for your review.

| Phase | Delivers |
|---|---|
| 1. Spec ✓ | SPEC, MODEL, ARCHITECTURE, DECISIONS, Python reference model and checks |
| 2. Model + first app ✓ | Gradle project, CI with APK releases, `:model` in Kotlin matching the golden vectors, daily orders, and an installable app: onboarding, today's orders with energy chart, settings, rules and sources |
| 3. Data | Room + migrations, usage-events source, Health Connect source, detection pipeline, manual entry. First check on your phone: does the detector see your nights? |
| 4. Sleep + check-in | Debt on Today, 14-night history, editing, morning check-in; usual schedule updates from real nights so the 30-min steps progress |
| 5. Reminders + HUD ✓ (pulled forward, D-42) | Exact-alarm reminders, receivers, permission flow, per-order toggles, test order, set-alarm; HUD redesign |
| 6. Calibration + hardening | KSS fit, export/import, crash log, accessibility pass, DST/travel tests |
| 7. Two-week trial | You use it; we compare detected vs. confirmed nights and curve vs. your ratings, then fix what's off |
| 8+. Next modules | Training, nutrition, mobility, gut log, one at a time. Each repeats the pattern: research and spec with sourced rules → model in `:model` with tests → screens and orders. Order of modules to be decided |
