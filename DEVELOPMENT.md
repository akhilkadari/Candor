# Development Guide

This guide explains how to run, configure, and verify Candor locally without changing application behavior.

## 1. Project Layout

### Clone The Repository

```sh
git clone https://github.com/akhilkadari/Candor.git
cd Candor
```

- Root repository: documentation, workflow files, allowlists, and project metadata.
- Android app: `Android/src`
- Main application module: `Android/src/app`

Most commands in this guide assume:

```sh
cd Android/src
```

## 2. Prerequisites

- Android Studio with SDK 35 installed
- Java 21 available for Gradle and CI parity
- Android 12+ target device or emulator
- A Hugging Face account and OAuth application

## 3. Required Configuration

Candor inherits its model download flow from Google AI Edge Gallery. To download models locally, you must configure your own Hugging Face OAuth application.

### Update `ProjectConfig.kt`

Edit [ProjectConfig.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/common/ProjectConfig.kt:1) and replace:

- `clientId`
- `redirectUri`

### Update the redirect scheme placeholder

Edit [build.gradle.kts](/Users/akhilkadari/Desktop/Candor/Android/src/app/build.gradle.kts:1) and replace:

- `manifestPlaceholders["appAuthRedirectScheme"]`

The scheme must match the redirect URI registered in your Hugging Face OAuth app.

## 4. Build Commands

### Release build

```sh
./gradlew assembleRelease
```

### Focused Kotlin compile

```sh
./gradlew :app:compileReleaseKotlin
```

### Debug install

```sh
./gradlew installDebug
```

## 5. How To Exercise The App

### First launch

1. Open the app.
2. Navigate to the Candor recovery flow.
3. Use the Log tab to create a daily check-in.

### Enable insight generation

1. Download or load the preferred Gemma model through the model management flow.
2. Ensure the downloaded artifact matches the expected file:
   `gemma-4-E2B-it_qualcomm_sm8750.litertlm`
3. Save at least 5 entries.
4. Open the Insights tab and trigger insight generation.

### Development shortcut

`RecoveryViewModel` includes mock-data seeding helpers for development scenarios:

- `STEADY_PROGRESS`
- `DOWNWARD_SPIRAL`
- `VOLATILE_ROLLERCOASTER`

These are useful for exercising the insight system quickly during local validation.

## 6. Persistence Model

Candor uses two local persistence paths:

- Proto DataStore
  `checkins.pb` stores daily check-in entries.
- Room
  `recovery.db` stores serialized insight snapshots and recovery database entities.

This split is intentional:

- Check-ins are append/update style and map well to Proto DataStore.
- Generated insights are better handled as snapshot records in Room.

## 7. Implementation Notes That Matter During Setup

### Minimum supported Android

- `minSdk = 31`
- Targeted platform is Android 12+

### ABI expectation

- `arm64-v8a` is the configured ABI filter for the app module.

### Current app orientation

- Portrait mode is enforced in the main activity.

### Model behavior

The insights flow does not use a separate cloud service. It initializes the local model and tries accelerators in this order:

`NPU/TPU -> GPU -> CPU`

## 8. CI Notes

GitHub Actions builds from:

- Working directory: `Android/src`
- Java version: `21`
- Command: `./gradlew assembleRelease`

See [.github/workflows/build_android.yaml](/Users/akhilkadari/Desktop/Candor/.github/workflows/build_android.yaml:1).

## 9. Common Pitfalls

### Build succeeds locally but model download fails

Usually caused by missing or mismatched Hugging Face OAuth configuration.

### Insights tab says no model is available

Candor looks for:

- name: `Gemma-4-E2B-it`
- file: `gemma-4-E2B-it_qualcomm_sm8750.litertlm`

If another model is loaded, the recovery insights flow will not treat it as the preferred analysis model.

### Insights cannot be generated yet

The app requires at least 5 saved entries before Gemma-based insight generation becomes available.

### Release build warnings

There are currently non-blocking warnings in the Android build, including deprecation and Kotlin compiler warnings. They do not prevent the release Kotlin compile from succeeding.

## 10. Suggested Verification Pass

Before a demo or submission, verify:

1. `./gradlew :app:compileReleaseKotlin`
2. The Log tab can save a same-day entry.
3. A saved entry appears in History.
4. At least 5 entries exist.
5. The preferred Gemma model is downloaded.
6. The Insights tab can generate and persist a snapshot.

## 11. Documentation Map

- Root overview: [README.md](README.md)
- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Implementation history: [docs/IMPLEMENTATION_HISTORY.md](docs/IMPLEMENTATION_HISTORY.md)
- Android-specific orientation: [Android/README.md](Android/README.md)
