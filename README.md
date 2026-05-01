# Candor

Candor is a privacy-first Android recovery companion built for on-device reflection, pattern tracking, and insight generation. Users log a daily check-in, review their recent history, and generate AI-assisted observations from their own data without sending their journal history to a remote inference service.

This project is based on Google AI Edge Gallery and has been narrowed into a focused recovery experience for hackathon submission. The current product centers on one workflow: log your day, detect patterns, and surface useful signals entirely on device once the model is installed.

## What Candor Does

- Guides the user through a daily recovery check-in with progressive disclosure.
- Tracks five core recovery ratings: craving intensity, mood, stress level, social connection, and self-efficacy.
- Supports trigger tagging alongside structured daily ratings.
- Captures freeform reflection alongside structured inputs.
- Persists daily entries locally on device.
- Generates rule-based signals such as streaks, consistency summaries, and early warning indicators.
- Generates AI insights from recent entries using an on-device Gemma 4 E2B model.
- Lets users review history and edit previous entries from the history flow.

## Why It Matters

Recovery tools often force a tradeoff between usefulness and privacy. Candor is designed to avoid that tradeoff:

- Personal check-in data stays on device.
- Insight generation runs locally after the model has been downloaded.
- Users get pattern detection without needing to trust a cloud analysis pipeline.

The only network-dependent part of the core experience is initial model authentication and download through Hugging Face and the model management flow inherited from AI Edge Gallery.

## Product Walkthrough

### 1. Log

The Log tab is the main daily entry point.

- Starts with a single `Log Your Day` CTA instead of dumping the full form immediately.
- Reveals each question progressively as the user moves through the check-in.
- Uses semantic sliders for the core metrics.
- Supports trigger tagging using a recovery-oriented taxonomy.
- Saves one entry per day and updates existing entries for the same date.

### 2. Insights

The Insights tab combines deterministic analysis with LLM reasoning.

- Requires at least 5 check-ins before Gemma insights are available.
- Builds a prompt from up to the 30 most recent entries.
- Attempts the preferred accelerator order during model initialization:
  `NPU/TPU -> GPU -> CPU`
- Stores generated insight snapshots locally so prior results remain visible.

Candor currently prefers:

- Model name: `Gemma-4-E2B-it`
- File: `gemma-4-E2B-it_qualcomm_sm8750.litertlm`

### 3. History

The History flow lets the user inspect prior entries and return an entry to the Log screen for editing. Candor also computes streak and consistency information from the saved timeline.

## Technical Highlights

- Android app written in Kotlin with Jetpack Compose.
- Hilt-based dependency injection.
- Proto DataStore for check-in persistence.
- Room for stored insight snapshots and recovery-related data access.
- LiteRT / Google AI Edge runtime for on-device LLM execution.
- Gemma 4 E2B used as the primary analysis model for the recovery insight workflow.

## Architecture At A Glance

The current app is structured around a focused recovery flow:

- `RecoveryApp.kt`
  Primary three-tab experience: Log, Insights, History.
- `RecoveryViewModel.kt`
  Owns check-in form state, saving, editing, and mock data seeding.
- `InsightsViewModel.kt`
  Loads saved snapshots, checks model availability, initializes Gemma, builds prompts, and saves generated insight results.
- `InsightsEngine.kt`
  Pure Kotlin analysis helpers for consistency and early-signal detection.
- `CheckInRepository.kt`
  Local repository over `checkins.pb` Proto DataStore.
- `RecoveryDatabase.kt` and `InsightRepository.kt`
  Local Room storage for serialized insight snapshots.

For a fuller breakdown, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Repository Guide

- [DEVELOPMENT.md](DEVELOPMENT.md)
  Local setup, build steps, model setup, and common pitfalls.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
  App layers, data flow, and the recovery-specific implementation.
- [docs/IMPLEMENTATION_HISTORY.md](docs/IMPLEMENTATION_HISTORY.md)
  The major implementation phases, derived from commit history.
- [Android/README.md](Android/README.md)
  Android module-focused orientation.
- [Bug_Reporting_Guide.md](Bug_Reporting_Guide.md)
  How to capture actionable Android bug reports.
- [Function_Calling_Guide.md](Function_Calling_Guide.md)
  Notes on the inherited function-calling surface from AI Edge Gallery.

## Quick Start

### Requirements

- macOS or Linux development environment
- Android Studio
- Android SDK for API 35
- An Android 12+ device or emulator
- A Hugging Face OAuth app for model download

### Clone

```sh
git clone https://github.com/akhilkadari/Candor.git
cd Candor
```

### Build

From the Android project root:

```sh
cd Android/src
./gradlew assembleRelease
```

For detailed setup, including OAuth placeholders, see [DEVELOPMENT.md](DEVELOPMENT.md).

## Demo Notes For Reviewers

If you are reviewing Candor for a hackathon submission, the intended flow is:

1. Open Candor and start a daily check-in from the Log tab.
2. Save several entries or seed mock data during development.
3. Load the preferred Gemma 4 E2B model through the model management flow.
4. Generate insights from the Insights tab.
5. Review history and edit a saved day from the History tab.

## Current Status

Candor is in late-stage hackathon polish. The core recovery flow, local persistence, and on-device insight generation are implemented. Current documentation is aimed at making the project easy to evaluate, demo, and extend.

## Acknowledgements

- Built on top of Google AI Edge Gallery.
- Uses Google AI Edge / LiteRT infrastructure for on-device inference.
- Uses Gemma-family models for private local analysis.

## License

Candor is licensed under the Apache License 2.0. See [LICENSE](LICENSE).
