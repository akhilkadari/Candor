# Architecture

Candor is a focused Android application layered on top of the Google AI Edge Gallery codebase. This document explains the current architecture of the submitted product rather than every inherited subsystem from the upstream base.

## High-Level Shape

Candor is organized around one primary experience:

1. Capture a daily recovery check-in.
2. Persist it locally.
3. Analyze recent patterns with deterministic logic and an on-device LLM.
4. Surface history and editable entries.

## Core Layers

### UI Layer

Primary recovery UI lives in:

- [RecoveryApp.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/RecoveryApp.kt:1)
- [RecoveryApp.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/RecoveryApp.kt:448)

Responsibilities:

- Tabbed recovery experience
- Progressive check-in interaction
- Insight display states
- History rendering inside the recovery flow
- Edit-entry handoff back into the log flow

### ViewModel Layer

- [RecoveryViewModel.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/RecoveryViewModel.kt:1)
- [InsightsViewModel.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/InsightsViewModel.kt:1)

Responsibilities:

- Form state and validation behavior
- Save/edit/reset mechanics
- Loading saved history
- Model readiness checks
- Prompt construction and inference orchestration
- Saving and restoring insight snapshots

### Analysis Layer

- [InsightsEngine.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/InsightsEngine.kt:1)

Responsibilities:

- Compute consistency and streak-related summaries
- Detect recent early-signal changes from check-in history
- Build the structured prompt used for Gemma-based analysis

This file is intentionally pure Kotlin and carries no Android UI dependency.

### Persistence Layer

#### Proto DataStore

- [checkin.proto](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/proto/checkin.proto:1)
- [CheckInRepository.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/CheckInRepository.kt:1)
- [AppModule.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/di/AppModule.kt:1)

Used for:

- daily check-in entries
- one entry per date
- local append/replace semantics

#### Room

- [RecoveryDatabase.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/recovery/RecoveryDatabase.kt:1)
- [InsightRepository.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/recovery/InsightRepository.kt:1)
- [DatabaseModule.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/di/DatabaseModule.kt:1)

Used for:

- stored insight snapshots
- recovery-oriented database entities
- snapshot retrieval between sessions

## Data Model

The active daily check-in model currently includes:

- `date`
- `craving_intensity`
- `mood`
- `stress_level`
- `social_connection`
- `self_efficacy`
- `triggers`
- `reflection`
- `timestamp_ms`

These fields are defined in `checkin.proto` and are the canonical persisted shape for the current Candor check-in flow.

## Insight Generation Flow

### Deterministic Layer

Candor first computes lightweight logic locally:

- check-in streaks
- missed days this week
- recent pattern shifts
- evidence strings comparing recent entries to baseline behavior

This makes the app useful even before a model result is generated.

### Gemma Layer

`InsightsViewModel` then attempts to generate richer observations using the preferred Gemma 4 E2B model.

The current flow:

1. Confirm the preferred model is available.
2. Ensure the user has at least 5 entries.
3. Initialize the model on the best available accelerator.
4. Build a prompt from recent entries.
5. Stream and parse the JSON-like response.
6. Save a snapshot locally for later display.

### Accelerator Strategy

The current preferred order is:

- `NPU`
- `TPU`
- `GPU`
- `CPU`

This fallback behavior is implemented in `InsightsViewModel.kt`.

## Dependency Injection

Candor uses Hilt for repository and database provisioning.

Important module boundaries:

- `AppModule.kt`
  DataStore, serializers, and repositories.
- `DatabaseModule.kt`
  Room database and DAOs.

## Main Activity Role

[MainActivity.kt](/Users/akhilkadari/Desktop/Candor/Android/src/app/src/main/java/com/google/ai/edge/gallery/MainActivity.kt:1) still carries some responsibilities from the AI Edge Gallery base:

- splash handling
- app startup
- deep link handling
- model allowlist load
- top-level content bootstrap

Candor’s recovery UI is then mounted from the navigation/app layer.

## Upstream Inheritance

This repository still includes systems inherited from AI Edge Gallery:

- model manager infrastructure
- task definitions
- LLM chat code paths
- allowlist handling

Candor intentionally narrows the visible product surface to the recovery workflow, but the architecture still benefits from that mature on-device model runtime and management stack.
