# Commander architecture

## Platform

- Android only, Kotlin, Jetpack Compose (Material 3).
- **minSdk 34** (Android 14), where Health Connect is part of the platform. compileSdk/targetSdk 35. Library versions are pinned in `gradle/libs.versions.toml` (D-30).
- Package and application id `com.adhavaa.commander`. Every build is signed with the same key (D-28) and numbered by the CI run, so each new APK installs over the last.
- Single user, sideloaded. No `INTERNET` permission in the manifest.

## Modules

```
neutron/
├── model/        pure Kotlin/JVM, no Android dependencies: sleep (curve, need, debt, detector, plan) and fuel
├── app/          Compose UI, ViewModels, storage (data/), food search (fuel/), order text, background work, notifications
└── tools/model-reference/   Python reference model + checks (golden-vector source)
```

| Module | Owns | Depends on |
|---|---|---|
| `:model` | Three-process curve, sleep need, sleep debt, rest-period detector, daily plan, real-night history; Fuel: resting energy, trend weight, adaptive expenditure, check-ins and targets | nothing (Kotlin stdlib, `java.time`) |
| `:app` | Screens (`ui/`, HUD components in `Hud.kt`), ViewModels, storage (`data/`: Room, DataStore, repositories), food parsing, search and recipes (`fuel/`, pure Kotlin), order and reminder wording (`orders/`, pure Kotlin), notifications and exact alarms (`notify/`), screen-use reading. One `AppContainer` instead of Hilt (D-46) | `:model` |

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

Built so far (schema version 2). Times are epoch milliseconds read in the phone's current zone (D-33); days are epoch days.

| Table | Columns | Since |
|---|---|---|
| `sleep` | id, startMs, endMs, kind (NIGHT / NAP), source (PHONE / MANUAL), confirmed, wokeNaturally (nullable), createdMs | v1 |
| `skipped_night` | epochDay ("I didn't sleep") | v1 |
| `order_log` | epochDay, kind, status, atMs (ticked orders) | v1 |
| `rating` | id, atMs, kss (1–9) | v1 |
| `food` | id, name, kcal, protein, fat, carb, fiber (per 100 g), servings ("label=grams\|…"), diet, ingredients (recipes: "ref=grams\|…"), cookedG, portions, archived, createdMs | v2 |
| `food_log` | id, epochDay, meal, ref (usda:id / food:id / quick), name, grams (null for quick add), amount, kcal, protein, fat, carb, fiber (a snapshot), atMs | v2 |
| `weight` | epochDay, kg, atMs (one per day) | v2 |
| `fuel_day` | epochDay, incomplete ("not fully logged") | v2 |

The USDA foods are not in Room: they ship as `assets/foods.tsv.gz` (220 KB, built by `tools/food-db/build_food_db.py`) and are loaded into memory on first search.

Planned for later phases: the screen-count signal per night (so detection can be re-run), calibration fits and a time-zone history.

DataStore holds settings: schedule, target wake, need override, reminders, free days, campaign start, and the Fuel profile and practices.

Each version bump ships a written migration with a test that opens the previous version's database (`MigrationTest`); the database is never destructively rebuilt. CI attaches Room's exported schema JSON to each release.

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
| `:model` | JUnit tests against golden vectors from `tools/model-reference` (curves, debt, need, acrophase, detector, daily orders, real-night histories, fuel trend and check-ins; tolerance 1e-6), plus behaviour tests of the orders and rules of command |
| `:app` | Pure tests for order text, reminders, brief, campaign, food parsing, diet filters, search, recipes and fuel targets (including the real USDA file); Robolectric tests for Room repositories and the v1→v2 migration, that alarms are set and a fired order becomes a notification; render tests that draw every screen to PNG (attached to each release) and fail if blank |
| CI (GitHub Actions) | On every push: Python reference checks, golden freshness (`make_golden.py --check`), `:model:test`, `:app:testReleaseUnitTest`, `:app:lintRelease`, `:app:assembleRelease`, a check that the APK has no INTERNET permission, then a GitHub release with the APK on `main`. Emulator tests run locally, not in CI |

**Where things get built.** The cloud workspace these sessions run in can't reach Google's Maven repository, Maven Central or the Gradle distribution servers, so Android builds and tests run in GitHub Actions and on your machine. Each phase is pushed and is only called done once CI is green.

## Phases

Each phase ends with tests run, a pass/fail report, known gaps listed, and a stop for your review.

| Phase | Delivers |
|---|---|
| 1. Spec ✓ | SPEC, MODEL, ARCHITECTURE, DECISIONS, Python reference model and checks |
| 2. Model + first app ✓ | Gradle project, CI with APK releases, `:model` in Kotlin matching the golden vectors, daily orders, and an installable app: onboarding, today's orders with energy chart, settings, rules and sources |
| 3. Real nights ✓ | Room, phone-use detection, morning report, sleep tab, need learning, rhythm stats, naps, ticks, ratings. Health Connect deferred (D-47) |
| 4. Fuel ✓ | Food log on the USDA database, custom foods and recipes, weight trend, adaptive expenditure, targets, weekly check-in, weigh-in order (MODEL.md §10) |
| 5. Reminders + HUD ✓ (pulled forward, D-42) | Exact-alarm reminders, receivers, permission flow, per-order toggles, test order, set-alarm; HUD redesign |
| 6. Calibration + hardening | KSS fit, export/import, crash log, accessibility pass, DST/travel tests |
| 7. Two-week trial | You use it; we compare detected vs. confirmed nights and curve vs. your ratings, then fix what's off |
| 8. Train (next) | Strength logging and progression, triathlon load and race phases, mobility routines. Starts with a sourced spec |
| 9. Gut log | Stool type, symptoms, patterns against food |
