# Implementation History

This document summarizes how Candor was implemented, based on the repository’s commit history. It is intended to help reviewers understand the engineering progression behind the final submission.

## Phase 1: Recovery App Shell

Early work established the dedicated three-screen recovery experience:

- `c35bf88`
  Introduced the three-screen UI foundation.

This shifted the product away from a generic model playground and toward a focused app flow.

## Phase 2: Persistence And Recovery Data

The next major step was turning the UI scaffold into a real application with saved state and a recovery-specific data layer.

- `0850191`
  Added the Room database layer for entries and insights.
- `623a9ec`
  Implemented the Gemma 4 E2B insights engine and recovery data collection.
- `52da30c`
  Added history support and retrieval helpers.

This phase created the core product loop:

- log a day
- store it locally
- reason over accumulated history

## Phase 3: Insight Reliability And Prompting Iteration

The insight system then went through multiple stabilization passes.

- `6cb2294`
- `19c9bf3`
- `519f8cf`
- `40fa5a5`
- `71e8807`
- `b1981f0`
- `51985d2`
- `c52acab`
- `662be6e`

These commits reflect prompt tuning, parsing fixes, fallback behavior, and general robustness work around the insight experience.

## Phase 4: UI Polish And Candor Identity

Once the core behavior existed, the project moved into product polish and rebranding.

- `8751ab0`
  Polished the Recovery UI with Candor branding, icons, semantic sliders, and day badges.
- `79544fd`
  Improved card styling and visual hierarchy.
- `c539a5f`
  Removed unused AI Edge Gallery features and rebranded the app to Candor.
- `9987190`
  Simplified the Log page with a single-button start and progressive question visibility.
- `4dc747f`
  Final cleanup of the modernized Log UI.

This phase is where the app stopped feeling like a fork and started reading as a coherent product.

## Phase 5: Build And CI Stabilization

Late-stage work focused on keeping the release build healthy and making the project submission-ready.

- `148d13b`
  Fixed CI build issues in repository configuration.
- `6ca60b9`
  Fixed a release Kotlin compile failure in `ModelManagerViewModel.kt`.
- `5161ccf`
  Final follow-up fix before merge.
- `2af6872`
  Merged the cleanup and rebranding PR into `main`.

## What Changed Strategically

Looking across the commits, the project evolved in three strategic directions:

### 1. Scope Reduction

The team deliberately removed or de-emphasized generic AI playground features so the submission would center on one strong use case.

### 2. On-Device Intelligence With Guardrails

Candor did not rely only on freeform model output. It pairs:

- deterministic consistency logic
- structured prompt building
- local snapshot persistence
- accelerator fallback behavior

That combination makes the insight experience more stable and more explainable.

### 3. Demo-Focused UX

The later commits show a clear shift toward usability:

- simpler entry flow
- better visual identity
- clearer progress cues
- easier history review
- safer release build behavior

## Why This Matters For Review

The commit history shows that Candor was not assembled as a static mockup. It was iteratively built as:

1. a recovery-specific product concept,
2. a functioning local data pipeline,
3. an on-device LLM analysis workflow,
4. and finally a polished submission-ready Android app.
