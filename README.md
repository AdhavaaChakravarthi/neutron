# Neutron

A personal Android app that estimates your sleep need and sleep debt from phone usage and shows when in the day you're likely to feel sharp or sleepy. Built on published sleep research; every number is sourced in [MODEL.md](MODEL.md).

**Status:** Phase 1 (spec) complete. No app code yet.

| Doc | What's in it |
|---|---|
| [SPEC.md](SPEC.md) | What the app does, screen by screen |
| [MODEL.md](MODEL.md) | The science: equations, parameters, sources, known limits |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, data model, background work, build phases |
| [DECISIONS.md](DECISIONS.md) | Every assumption, and the open decisions |
| [CLAUDE.md](CLAUDE.md) | Working rules for Claude sessions on this repo |

Run the model checks:

```
cd tools/model-reference && python3 check_model.py --plot
```

Neutron is a wellness tool, not a medical device.
