# Candor Android Module

This directory contains the Android implementation of Candor.

## Structure

- `src/`
  Gradle project root used by local builds and CI.
- `src/app/`
  Main Android application module.
- `src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/`
  Recovery-specific UI, view models, and insight logic.
- `src/app/src/main/java/com/google/ai/edge/gallery/data/`
  Repositories, configs, allowlist handling, and persistence adapters.
- `src/app/src/main/proto/`
  Proto definitions used by local DataStore persistence.

## What Lives Here

The Android module contains:

- The three-tab Candor recovery experience
- Local persistence for daily check-ins
- The on-device Gemma insight generation flow
- Model management inherited and narrowed from AI Edge Gallery
- Android-specific DI, manifest, build, and runtime setup

## Important Entry Points

- [MainActivity.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/MainActivity.kt:1)
- [RecoveryApp.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/RecoveryApp.kt:1)
- [RecoveryViewModel.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/RecoveryViewModel.kt:1)
- [InsightsViewModel.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/InsightsViewModel.kt:1)
- [CheckInRepository.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/CheckInRepository.kt:1)

## Build

From this directory's Gradle root:

```sh
cd src
./gradlew assembleRelease
```

For full setup instructions, go back to [DEVELOPMENT.md](../DEVELOPMENT.md).
