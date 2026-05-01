# Candor 🧠📱

Candor is a **privacy-first Android recovery companion** built for on-device reflection, pattern tracking, and insight generation. Users log a daily check-in, review their recent history, and generate AI-assisted observations — all without sending their data to the cloud.

This project is based on Google AI Edge Gallery and has been refined into a focused recovery experience for a hackathon setting. The core idea is simple:

> Log your day → detect patterns → surface insights — all on-device.

---

## ✨ What Candor Does

- Guides users through a **daily recovery check-in** with progressive disclosure.
- Tracks five core signals:
  - Craving intensity  
  - Mood  
  - Stress level  
  - Social connection  
  - Self-efficacy  
- Supports **trigger tagging** alongside structured inputs.
- Captures **freeform reflection** for added context.
- Stores all entries **locally on device** 🔒
- Generates:
  - Streaks and consistency summaries  
  - Early warning signals  
  - AI-powered insights using an on-device Gemma model  
- Allows users to **review and edit past entries**

---

## 🔐 Why It Matters

Most recovery tools force a tradeoff between **privacy and usefulness**. Candor is designed to eliminate that:

- All personal data stays **on-device**
- AI insights are generated **locally**
- No need to trust a cloud-based pipeline with sensitive information

The only network dependency is during **initial model download** via Hugging Face.

---

## 📲 Product Walkthrough

### 1. Log

The **Log tab** is the primary entry point.

- Starts with a simple **“Log Your Day”** CTA  
- Reveals questions progressively (low friction)  
- Uses intuitive sliders for core metrics  
- Supports structured trigger tagging  
- Saves one entry per day (updates if re-logged)

---

### 2. Insights

The **Insights tab** combines rule-based logic with LLM reasoning.

- Requires **≥ 5 check-ins** before AI insights activate  
- Builds prompts from up to **30 recent entries**  
- Uses hardware acceleration in this order:  
  `NPU/TPU → GPU → CPU ⚡`  
- Stores generated insights locally for persistence  

**Model Used:**
- `Gemma-4-E2B-it`
- `gemma-4-E2B-it_qualcomm_sm8750.litertlm`

---

### 3. History

The **History tab** lets users:

- Scroll through past entries  
- Edit previous days  
- View streaks and consistency trends 📊  

---

## ⚙️ Technical Highlights

- Kotlin + Jetpack Compose Android app  
- Hilt for dependency injection  
- Proto DataStore for check-in persistence  
- Room database for insight storage  
- LiteRT / Google AI Edge runtime for on-device inference  
- Gemma 4 E2B for local reasoning  

---

## 🏗️ Architecture At A Glance

Candor is structured around a focused recovery flow:

- `RecoveryApp.kt`  
  → Main 3-tab experience (Log, Insights, History)

- `RecoveryViewModel.kt`  
  → Manages check-in state and saving logic

- `InsightsViewModel.kt`  
  → Handles model loading, prompt building, and insight generation

- `InsightsEngine.kt`  
  → Rule-based pattern detection

- `CheckInRepository.kt`  
  → Local check-in storage via Proto DataStore

- `RecoveryDatabase.kt` + `InsightRepository.kt`  
  → Room storage for generated insights

For more detail, see:  
👉 `docs/ARCHITECTURE.md`

---

## 📁 Repository Guide

- `DEVELOPMENT.md` → Setup + build instructions  
- `docs/ARCHITECTURE.md` → System design + data flow  
- `docs/IMPLEMENTATION_HISTORY.md` → Development evolution  
- `Android/README.md` → Android-specific notes  
- `Bug_Reporting_Guide.md` → Debugging guidance  
- `Function_Calling_Guide.md` → AI Edge Gallery notes  

---

## 🚀 Quick Start

### Requirements

- macOS or Linux  
- Android Studio  
- Android SDK (API 35)  
- Android 12+ device or emulator  
- Hugging Face OAuth app  

---

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
