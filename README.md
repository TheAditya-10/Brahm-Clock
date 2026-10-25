# Brahm Clock

Lightweight Android clock app with an adaptive **Brahma Muhurta** alarm that
follows your local sunrise every day and wakes you gently in stages.

See [docs/PLAN.md](docs/PLAN.md) for the full product and technical plan.

## Build

```
./gradlew :core:test            # sunrise / Brahma engine tests (no Android SDK needed: add -PcoreOnly)
./gradlew :app:assembleRelease  # small, R8-shrunk APK
```

CI builds both APKs on every push; download `brahm-clock-apk` from the
workflow run's artifacts.
