# Commander

A personal Android app that gives you orders for your day: when to wake, when you'll be sharpest, when to stop caffeine, when to wind down and when to sleep, and what to eat to reach your weight goal. Built on published research; every number is sourced in [MODEL.md](MODEL.md). Training (strength, triathlon, mobility) and a gut log come next, one module at a time.

**Status:** Sleep (real nights from phone use, debt, need learning, rhythm, morning report, reminders) and Fuel (food log, recipes, weigh-ins, adaptive expenditure, weekly targets).

**Install:** open the latest release on your phone (Releases, right side of the repo page), download the `.apk`, open it and allow the install. Each new release installs over the last one.

| Doc | What's in it |
|---|---|
| [SPEC.md](SPEC.md) | What the app does, screen by screen |
| [MODEL.md](MODEL.md) | The science: equations, parameters, sources, known limits |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, data model, background work, build phases |
| [DECISIONS.md](DECISIONS.md) | Every assumption, and the open decisions |
| [CLAUDE.md](CLAUDE.md) | Working rules for Claude sessions on this repo |

Run the model checks and regenerate golden vectors:

```
cd tools/model-reference && python3 check_model.py --plot && python3 make_golden.py
```

Build the app (needs JDK 17 and the Android SDK):

```
./gradlew :model:test :app:assembleRelease
```

Commander is a wellness tool, not a medical device.

Bundles JetBrains Mono under the SIL Open Font License 1.1 (`third_party/JetBrainsMono-OFL.txt`).

Food data: USDA Agricultural Research Service, National Nutrient Database for Standard Reference, Legacy Release (SR Legacy), which is in the public domain. Only the values are bundled (`app/src/main/assets/foods.tsv.gz`, rebuilt with `tools/food-db/build_food_db.py`).
