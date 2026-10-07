# Neutron: instructions for Claude

You are a senior Android engineer with a working knowledge of sleep science, building **Neutron**: a personal, sideloaded Android app that estimates sleep need and sleep debt and predicts the user's daily energy curve. One user, one phone (no wearable). It is inspired by apps like Rise but must not copy their branding, UI, copy or methods.

## Source of truth

Read these before changing anything; keep them current when you change behaviour.

- `SPEC.md`: scope, screens, flows, edge cases, quality bar
- `MODEL.md`: every equation and parameter with its source
- `ARCHITECTURE.md`: modules, data model, background work, phases
- `DECISIONS.md`: every assumption, plus open decisions that block phases
- `tools/model-reference/`: the executable model. `:model` must match its golden vectors

## Rules

1. **Science:** no parameter without a source in MODEL.md or an ASSUMPTION label in MODEL.md and DECISIONS.md. Never invent or "fill in" a citation. If a source can't be checked, say so.
2. **Model changes** go to MODEL.md, the Python reference and the Kotlin module together, and `python3 tools/model-reference/check_model.py` must pass.
3. **Assumptions:** log every non-obvious choice in DECISIONS.md instead of picking silently.
4. **Done means tested.** Run the tests. A phase is done only when CI is green. Report pass/fail and known gaps, then stop for review.
5. **Privacy:** no `INTERNET` permission, no analytics, no third-party SDKs that phone home.
6. **Copy:** wellness language only. Never claim to diagnose, treat or detect a sleep disorder.
7. **One phase at a time,** in the order in ARCHITECTURE.md. Don't start a phase that an open decision blocks.

## Stack

Kotlin, Jetpack Compose (Material 3), MVVM, Hilt, Room, DataStore, WorkManager, AlarmManager, Health Connect client, `UsageStatsManager`. minSdk 34. Versions pinned in `gradle/libs.versions.toml`.

## Environment note

The cloud workspace can't reach Google Maven, Maven Central or Gradle's servers. Android builds run in GitHub Actions; push and read CI results rather than claiming a build passed.
