# Commander

A personal Android app that gives you orders for your day: when to wake, when you'll be sharpest, when to stop caffeine, when to wind down and when to sleep. Built on published sleep research; every number is sourced in [MODEL.md](MODEL.md). Training, nutrition, mobility and a gut log come later, one module at a time.

**Status:** Phase 2 done: the first installable build gives today's orders from your usual schedule. Real-night detection is Phase 3.

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
