# Function Calling Guide

Candor is built on top of Google AI Edge Gallery, which includes broader agent and function-calling surfaces. Function calling is not the primary user-facing feature of the current Candor hackathon experience, but this repository still contains inherited infrastructure related to tool-driven model tasks.

## Why This File Exists

Candor narrowed the product scope to the recovery workflow:

- daily check-ins
- local persistence
- on-device insights
- history and edit flow

That means function calling is currently a secondary implementation concern rather than a headline feature.

## Current Practical Status

- The recovery experience does not depend on custom function-calling flows.
- The insight pipeline is prompt-driven and model-specific.
- Some AI Edge Gallery task infrastructure remains in the repository because Candor was adapted from that codebase rather than rebuilt from scratch.

## If You Need To Explore It

Start with:

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/Tasks.kt`

## Guidance

If the goal is to understand the submitted Candor product, focus first on the recovery flow and insight pipeline documented in:

- [README.md](README.md)
- [DEVELOPMENT.md](DEVELOPMENT.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
