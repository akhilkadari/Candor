# Plan: Data Collection + Insights Implementation

## Context

`RecoveryApp.kt` already exists as a fully-rendered UI scaffold with 3 tabs (Log, Insights, History), Material 3 sliders, chip multi-select, and a text note field. Everything is hardcoded `rememberSaveable` state with no persistence and the wrong field definitions. This plan wires in the correct research-based data fields, a real Proto DataStore persistence layer, and a live insights engine backed by rule-based Kotlin computation + on-device Gemma inference.

---

## Files to Create

### 1. `checkin.proto`
**Path:** `Android/src/app/src/main/proto/checkin.proto`

```proto
syntax = "proto3";
package com.google.ai.edge.gallery.proto;
option java_package = "com.google.ai.edge.gallery.proto";
option java_multiple_files = true;

message CheckInEntry {
  string date = 1;               // YYYY-MM-DD, one entry per day
  int32 craving_intensity = 2;   // 1–10
  int32 mood = 3;                // 1–10
  int32 sleep_quality = 4;       // 1–10
  int32 stress_level = 5;        // 1–10
  int32 social_connection = 6;   // 1–10 (higher = more connected)
  int32 self_efficacy = 7;       // 1–10 (Marlatt forward-looking confidence)
  float sleep_hours = 8;         // decimal hours, 0.0–24.0
  repeated string triggers = 9;  // Marlatt taxonomy multi-select keys
  string hardest_today = 10;     // qualitative free text
  string helped_today = 11;      // qualitative free text
  int64 timestamp_ms = 12;       // Unix epoch ms of submission
}

message CheckInCollection {
  repeated CheckInEntry entries = 1;
}
```

---

### 2. `CheckInSerializer.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/CheckInSerializer.kt`

Follows the exact pattern of `BenchmarkResultsSerializer.kt`:
```kotlin
object CheckInSerializer : Serializer<CheckInCollection> {
  override val defaultValue = CheckInCollection.getDefaultInstance()
  override suspend fun readFrom(input: InputStream) = CheckInCollection.parseFrom(input)
  override suspend fun writeTo(t: CheckInCollection, output: OutputStream) = t.writeTo(output)
}
```

---

### 3. `CheckInRepository.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/CheckInRepository.kt`

Interface + `DefaultCheckInRepository` implementation (same standalone pattern as `DownloadRepository`, not extending `DataStoreRepository`):

```kotlin
interface CheckInRepository {
  fun addOrReplaceEntry(entry: CheckInEntry)   // upsert by date
  fun getAllEntries(): List<CheckInEntry>       // sorted newest-first
  fun getRecentEntries(days: Int): List<CheckInEntry>
  fun deleteEntry(date: String)
}

class DefaultCheckInRepository(
  private val checkInDataStore: DataStore<CheckInCollection>
) : CheckInRepository { ... }
```

`addOrReplaceEntry` uses `date` as the upsert key (only one entry per calendar day).

---

### 4. `RecoveryViewModel.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/RecoveryViewModel.kt`

`@HiltViewModel`. Replaces all `rememberSaveable` state in `RecoveryLogScreen` and `RecoveryHistoryScreen`. Exposes:
- `logUiState: StateFlow<LogUiState>` — current slider values, trigger set, text fields
- `historyEntries: StateFlow<List<CheckInEntry>>` — real entries for History tab
- `fun saveEntry()` — writes `logUiState` to `CheckInRepository`

`LogUiState` data class mirrors `CheckInEntry` fields, all mutable via individual update functions.

---

### 5. `InsightsEngine.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/InsightsEngine.kt`

Pure Kotlin object (no ViewModel, no coroutines). Two responsibilities:

**A. Rule-based computations (no Gemma needed):**
- `computeEarlySignals(entries)` — compares each field's 3-day average vs. 14-day baseline. Returns `List<String>` like "Stress has increased over the past 2 days" or "Sleep quality is below your 2-week average."
- `computeConsistency(entries)` — calculates current check-in streak, missed days this week, total entries. Returns `ConsistencyInsight(streak, missedThisWeek, message)`.

**B. Gemma prompt builder:**
- `buildInsightPrompt(entries)` — serializes the last 30 entries (or all if fewer) into a compact structured summary and wraps it with a tightly-scoped system instruction:

```
You are analyzing recovery check-in data. Identify exactly:
1. TWO multi-signal patterns that correlate with higher cravings or worse days
   (must reference ≥2 fields, e.g. sleep + stress, not single-field observations)
2. TWO protective factors that correlate with lower cravings or better days

Data: [structured table of entries]

Rules: Be specific. Reference the user's actual values. No generic advice.
Use second person. Each insight is 1-2 sentences.
Output JSON only: {"patterns": ["...", "..."], "protective": ["...", "..."]}
```

---

### 6. `InsightsViewModel.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/InsightsViewModel.kt`

`@HiltViewModel`. Manages the full insights lifecycle:
- On init: loads entries from `CheckInRepository`, runs `InsightsEngine.computeEarlySignals()` and `computeConsistency()` synchronously (fast, rule-based)
- `generateGemmaInsights(model: Model, scope: CoroutineScope)` — calls `InsightsEngine.buildInsightPrompt()`, then routes through `model.runtimeHelper.runInference()` (the same path used by `LlmChatModelHelper`). Parses the JSON response into `patterns` and `protective` lists.

State:
```kotlin
data class InsightsUiState(
  val earlySignals: List<String>,
  val consistency: ConsistencyInsight,
  val patterns: List<String>,        // Gemma-generated
  val protective: List<String>,      // Gemma-generated
  val gemmaStatus: GemmaInsightStatus,  // IDLE | LOADING | DONE | ERROR
  val evidenceCount: Int,            // how many entries the insights are based on
)
```

If fewer than 5 entries exist: suppress Gemma sections with a "Keep logging — patterns will appear after a few more check-ins" message.

---

## Files to Modify

### 7. `AppModule.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/di/AppModule.kt`

Add three providers following the exact existing pattern:
```kotlin
@Provides @Singleton
fun provideCheckInSerializer(): Serializer<CheckInCollection> = CheckInSerializer

@Provides @Singleton
fun provideCheckInDataStore(@ApplicationContext context: Context, ...): DataStore<CheckInCollection> =
  DataStoreFactory.create(serializer = ..., produceFile = { context.dataStoreFile("checkins.pb") })

@Provides @Singleton
fun provideCheckInRepository(dataStore: DataStore<CheckInCollection>): CheckInRepository =
  DefaultCheckInRepository(dataStore)
```

---

### 8. `RecoveryApp.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/RecoveryApp.kt`

#### Log Screen changes:
Replace the 6 `rememberSaveable` floats with `RecoveryViewModel` state. Field updates:

| Current label | New label | Low label | High label | Note |
|---|---|---|---|---|
| Cravings | Craving Intensity | Quiet | Loud | — |
| Mood | Mood | Low | Steady | — |
| Sleep Quality | Sleep Quality | Restless | Rested | — |
| Stress | Stress Level | Calm | Overloaded | — |
| Isolation | Social Connection | Isolated | Connected | Framing flipped |
| Routine Stability | Self-Efficacy | Doubtful | Confident | New concept |

Add after sliders:
- **Sleep Hours field** — `OutlinedTextField` with `KeyboardType.Decimal`, label "Hours slept last night", single-line, validates 0.0–24.0 range

Update trigger chips to Marlatt's 8-category taxonomy:
```
"Interpersonal conflict", "Social pressure to use", "Substance cues (places/people)",
"Financial stress", "Work stress", "Physical pain/discomfort",
"Positive event / celebration", "None"
```

Replace single `OutlinedTextField` note with two fields:
- `"What was hardest today?"` — placeholder: "A situation, feeling, or moment that felt risky or difficult..."
- `"What helped today?"` — placeholder: "A coping strategy, person, routine, or thought that made things easier..."

Wire Save button to `RecoveryViewModel.saveEntry()`. Show `"Saved"` confirmation snackbar on success.

#### Insights Screen changes:
Accept `InsightsViewModel` and `ModelManagerViewModel`. Render 5 sections dynamically:

1. **Early Signals** (rule-based, always shown) — bullet list from `earlySignals`
2. **Recurring Patterns** (Gemma) — "Based on X entries" subtitle, bullet list from `patterns`
3. **Protective Factors** (Gemma) — bullet list from `protective`
4. **Consistency** (rule-based) — streak, missed days, engagement message
5. **Evidence** (existing card) — show last 3 entries that drove the insights

Show a `"Generate Insights"` button when `gemmaStatus == IDLE` and a model is loaded. Show a loading state during `LOADING`. Require minimum 5 entries before showing the Gemma sections.

#### History Screen changes:
Replace static `remember { listOf(...) }` with `collectAsState` from `RecoveryViewModel.historyEntries`. Map each `CheckInEntry` to `HistoryEntryUi`. Derive `headline` from highest/lowest field values (e.g. "Craving-heavy day, good sleep"), `riskLabel` from craving + stress composite score, `tags` from the `triggers` list.

---

## Implementation Order

1. `checkin.proto` → run Gradle to generate Java classes
2. `CheckInSerializer.kt`
3. `CheckInRepository.kt`
4. `AppModule.kt` additions
5. `RecoveryViewModel.kt`
6. `InsightsEngine.kt`
7. `InsightsViewModel.kt`
8. `RecoveryApp.kt` — Log Screen field updates + Save wiring
9. `RecoveryApp.kt` — History Screen data wiring
10. `RecoveryApp.kt` — Insights Screen dynamic rendering

---

## Verification

1. **Build:** `./gradlew assembleDebug` — must compile without errors after proto generation
2. **Log Tab:** Enter values, tap Save, force-kill app, reopen — values must persist in History tab
3. **History Tab:** Entries appear newest-first with correct date, derived headline, and risk label
4. **Insights Tab, rule-based:** Appears immediately with early signals and consistency data
5. **Insights Tab, Gemma:** With ≥5 entries and a model loaded, "Generate Insights" produces 2 patterns + 2 protective factors in natural language
6. **Sleep hours validation:** Entering "25" or letters shows an inline error; valid decimals (e.g. "6.5") are accepted
7. **One entry per day:** Saving a second check-in on the same day replaces the first (upsert by date)
