# Plan: Data Collection + Gemma 4 E2B Insights Engine

## Context

`RecoveryApp.kt` is a fully-rendered UI scaffold with three tabs (Log, Insights, History).
Everything is hardcoded `rememberSaveable` state — no persistence, wrong field labels, static
placeholder insights. The entire data layer and reasoning engine need to be built.

The goal: wire the correct research-based check-in fields to a Proto DataStore backend, then
use the on-device Gemma 4 E2B model as a reasoning engine for the Insights tab — completely
independent of the chat UI.

---

## How Gemma 4 E2B Fits In (Architecture Key)

The LiteRT-LM engine (`Engine` + `Conversation` objects) is what actually runs inference.
Once a model is initialized in the app (user loads it anywhere — chat, benchmark, etc.),
`model.instance` is set to a `LlmModelInstance(engine, conversation)` and stays live for
the app session.

**InsightsViewModel does NOT reuse the chat conversation.** Instead it creates a dedicated
short-lived `Conversation` from the same already-warm `Engine`:

```kotlin
val instance = model.instance as LlmModelInstance
val analysisConversation = instance.engine.createConversation(
    ConversationConfig(
        samplerConfig = SamplerConfig(topK = 1, temperature = 0.1),
        systemInstruction = Contents.of(listOf(Content.Text(SYSTEM_INSTRUCTION)))
    )
)
// run inference...
analysisConversation.close()   // release immediately after use
```

This means:
- No model re-initialization cost (engine is already warm)
- No interference with ongoing chat sessions
- Low temperature (0.1) gives deterministic, analytical output
- `topK = 1` (greedy) for consistent JSON output across runs
- Fresh context — no prior chat history bleeds in

**Model access path**: `InsightsViewModel` is injected with `ModelManagerViewModel`.
It calls `modelManagerViewModel.getModelByName("Gemma-4-E2B-it")`. If `model.instance != null`,
the engine is ready. If null, show a "Load model" CTA.

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
  string date = 1;              // YYYY-MM-DD, primary key (one entry per day)
  int32 craving_intensity = 2;  // 1-10, higher = worse
  int32 mood = 3;               // 1-10, higher = better
  int32 sleep_quality = 4;      // 1-10, higher = better
  int32 stress_level = 5;       // 1-10, higher = worse
  int32 social_connection = 6;  // 1-10, higher = better (replaces "isolation")
  int32 self_efficacy = 7;      // 1-10, Marlatt forward-looking confidence
  float sleep_hours = 8;        // decimal, objective sleep duration
  repeated string triggers = 9; // Marlatt taxonomy keys (see constants below)
  string hardest_today = 10;    // CBT prompt: high-risk situation
  string helped_today = 11;     // CBT prompt: coping response
  int64 timestamp_ms = 12;      // Unix epoch ms of submission
}

message CheckInCollection {
  repeated CheckInEntry entries = 1;
}
```

Trigger string keys (defined as constants in `InsightsEngine.kt`):
`interpersonal_conflict`, `social_pressure`, `substance_cues`, `financial_stress`,
`work_stress`, `physical_pain`, `positive_celebration`, `none`

---

### 2. `CheckInSerializer.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/CheckInSerializer.kt`

Exact same pattern as `BenchmarkResultsSerializer.kt`:
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

Standalone interface + implementation (same pattern as `DownloadRepository`, not extending
`DataStoreRepository`):

```kotlin
interface CheckInRepository {
  fun addOrReplaceEntry(entry: CheckInEntry)      // upsert keyed by entry.date
  fun getAllEntries(): List<CheckInEntry>           // sorted newest-first
  fun getRecentEntries(days: Int): List<CheckInEntry>
  fun deleteEntry(date: String)
  fun getEntryCount(): Int
}
```

`DefaultCheckInRepository(private val dataStore: DataStore<CheckInCollection>)`:
- `addOrReplaceEntry`: removes any existing entry with the same `date`, prepends the new one
- Uses `runBlocking { dataStore.updateData { ... } }` matching the existing sync pattern

---

### 4. `RecoveryViewModel.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/RecoveryViewModel.kt`

`@HiltViewModel`. Owns all Log-tab and History-tab state.

```kotlin
data class LogUiState(
  val cravingIntensity: Int = 5,
  val mood: Int = 5,
  val sleepQuality: Int = 5,
  val stressLevel: Int = 5,
  val socialConnection: Int = 5,
  val selfEfficacy: Int = 5,
  val sleepHours: String = "",           // raw string for text field; validated on save
  val sleepHoursError: String? = null,   // inline error message
  val selectedTriggers: Set<String> = emptySet(),
  val hardestToday: String = "",
  val helpedToday: String = "",
  val saveStatus: SaveStatus = SaveStatus.IDLE,
)

enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }
```

Key methods:
- `updateField(...)` — individual setters for each field, update `_logUiState`
- `toggleTrigger(key: String)` — add/remove from `selectedTriggers`
- `saveEntry()` — validates `sleepHours` float (0.0–24.0), constructs `CheckInEntry`
  with today's date (`LocalDate.now().toString()`), calls `checkInRepository.addOrReplaceEntry()`,
  sets `saveStatus = SAVED`, resets after 2 seconds
- `historyEntries: StateFlow<List<CheckInEntry>>` — exposes `checkInRepository.getAllEntries()`
  as a Flow by re-reading on each save

---

### 5. `InsightsEngine.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/InsightsEngine.kt`

Pure Kotlin object. No coroutines, no Android dependencies. Two responsibilities:

#### A. Rule-based computations (instant, no model needed)

```kotlin
fun computeEarlySignals(entries: List<CheckInEntry>): List<String>
```
- Requires ≥ 4 entries; returns empty list otherwise
- For each of the 7 numeric fields (6 sliders + sleep hours):
    - 3-day average: average of the 3 most recent entries
    - Baseline: average of entries from day 4 onward (or all if < 14 total)
    - Threshold: delta > 1.5 for 1-10 fields, > 1.0 hr for sleep hours
    - Generates natural-language strings, e.g.:
        - `"Your craving intensity has been higher over the past 3 days (avg 7.3 vs. your usual 4.8)."`
        - `"Sleep quality has been lower than your baseline recently (avg 3.1 vs. 6.2)."`
    - Field directionality: craving↑ and stress↑ are negative; mood↑, sleep↑, social↑, efficacy↑ are positive

```kotlin
data class ConsistencyInsight(
  val currentStreak: Int,
  val missedThisWeek: Int,
  val totalEntries: Int,
  val message: String
)

fun computeConsistency(entries: List<CheckInEntry>): ConsistencyInsight
```
- Streak: walk backward from today counting consecutive calendar days with an entry
- Missed this week: count Mon–Sun days with no entry
- `message` examples: `"5-day streak — keep it going."` / `"You've checked in 4 of 7 days this week."`

#### B. Gemma prompt builder

```kotlin
const val ANALYSIS_SYSTEM_INSTRUCTION = """
You are a private, on-device recovery data analyst.
Analyze personal check-in data and report patterns as brief, specific, data-grounded observations.
Output only valid JSON. No disclaimers, no advice, no markdown fences, no text outside the JSON object.
"""

fun buildInsightPrompt(entries: List<CheckInEntry>): String
```

Takes the 30 most recent entries (or all if fewer) and builds a structured prompt:

```
Field guide: craving_intensity (1-10, higher=worse), mood (1-10, higher=better),
sleep_quality (1-10, higher=better), sleep_hours (decimal), stress_level (1-10, higher=worse),
social_connection (1-10, higher=better), self_efficacy (1-10, higher=better),
triggers (pipe-separated tags), hardest_today (text), helped_today (text).

Entries (newest first):
DATE|CRAV|MOOD|SLP_Q|SLP_H|STRESS|SOCIAL|EFFICACY|TRIGGERS|HARDEST|HELPED
2026-05-01|7|4|3|5.5|8|2|3|work_stress|boss was rude|went for a walk
...

Return ONLY this JSON:
{
  "patterns": [
    {"text": "...", "evidence": "..."},
    {"text": "...", "evidence": "..."}
  ],
  "protective": [
    {"text": "...", "evidence": "..."},
    {"text": "...", "evidence": "..."}
  ]
}

Requirements:
- patterns: exactly 2 multi-signal observations; each must combine ≥2 different fields
- protective: exactly 2 positive correlations showing what correlates with better days
- text: 1-2 sentences in second person ("Your cravings tend to...")
- evidence: cite specific counts or timeframes ("on 4 of the past 7 days when...", "across your past week...")
- Use natural language only; never reference field codes like "craving_intensity"
```

---

### 6. `InsightsViewModel.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/InsightsViewModel.kt`

`@HiltViewModel`. The heart of the Gemma integration.

```kotlin
sealed class GemmaInsightStatus {
  object NoModel : GemmaInsightStatus()       // model not loaded
  object NotEnoughData : GemmaInsightStatus() // < 5 entries
  object Idle : GemmaInsightStatus()          // ready to generate
  object Loading : GemmaInsightStatus()       // inference running
  data class Done(
    val patterns: List<InsightItem>,
    val protective: List<InsightItem>,
    val entryCount: Int,
  ) : GemmaInsightStatus()
  data class Error(val message: String) : GemmaInsightStatus()
}

data class InsightItem(val text: String, val evidence: String)

data class InsightsUiState(
  val earlySignals: List<String> = emptyList(),
  val consistency: ConsistencyInsight? = null,
  val gemmaStatus: GemmaInsightStatus = GemmaInsightStatus.Idle,
  val streamingText: String = "",   // live partial output while Loading
)
```

**`init` block**: loads entries, runs `InsightsEngine.computeEarlySignals()` and
`computeConsistency()` synchronously — these are instant, always shown.

**`checkModelAvailability(modelManagerViewModel: ModelManagerViewModel)`**:
Calls `modelManagerViewModel.getModelByName("Gemma-4-E2B-it")`. If `model.instance == null` →
emit `GemmaInsightStatus.NoModel`. If entry count < 5 → emit `NotEnoughData`. Otherwise → `Idle`.

**`generateInsights(modelManagerViewModel: ModelManagerViewModel)`**:
```kotlin
fun generateInsights(modelManagerViewModel: ModelManagerViewModel) {
    viewModelScope.launch(Dispatchers.Default) {
        _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.Loading, streamingText = "") }

        val model = modelManagerViewModel.getModelByName("Gemma-4-E2B-it") ?: run {
            _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.NoModel) }
            return@launch
        }
        val instance = model.instance as? LlmModelInstance ?: run {
            _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.NoModel) }
            return@launch
        }

        val entries = checkInRepository.getRecentEntries(30)
        val prompt = InsightsEngine.buildInsightPrompt(entries)

        // Create a dedicated low-temp analysis conversation from the warm engine
        val analysisConversation = instance.engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(topK = 1, temperature = 0.1),
                systemInstruction = Contents.of(listOf(Content.Text(
                    InsightsEngine.ANALYSIS_SYSTEM_INSTRUCTION
                )))
            )
        )

        var rawResponse = ""
        try {
            analysisConversation.sendMessageAsync(
                Contents.of(listOf(Content.Text(prompt))),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        rawResponse += message.toString()
                        _uiState.update { it.copy(streamingText = rawResponse) }
                    }
                    override fun onDone() {
                        val parsed = parseGemmaResponse(rawResponse, entries.size)
                        _uiState.update { it.copy(gemmaStatus = parsed, streamingText = "") }
                    }
                }
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(
                gemmaStatus = GemmaInsightStatus.Error("Analysis failed: ${e.message}")
            )}
        } finally {
            analysisConversation.close()  // always release
        }
    }
}
```

**`parseGemmaResponse(raw: String, entryCount: Int): GemmaInsightStatus`**:
- Extract the first `{...}` JSON block from `raw` (model may emit extra text despite instructions)
- Parse with `Gson` into a simple data class
- Map to `GemmaInsightStatus.Done(patterns, protective, entryCount)`
- On parse failure → `GemmaInsightStatus.Error("Could not parse model output")`

---

## Files to Modify

### 7. `AppModule.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/di/AppModule.kt`

Add three providers following the exact existing pattern (copy structure from benchmark providers):

```kotlin
@Provides @Singleton
fun provideCheckInSerializer(): Serializer<CheckInCollection> = CheckInSerializer

@Provides @Singleton
fun provideCheckInDataStore(
  @ApplicationContext context: Context,
  serializer: Serializer<CheckInCollection>
): DataStore<CheckInCollection> =
  DataStoreFactory.create(
    serializer = serializer,
    produceFile = { context.dataStoreFile("checkins.pb") }
  )

@Provides @Singleton
fun provideCheckInRepository(
  dataStore: DataStore<CheckInCollection>
): CheckInRepository = DefaultCheckInRepository(dataStore)
```

---

### 8. `RecoveryApp.kt`
**Path:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/recovery/RecoveryApp.kt`

#### Log Screen — field corrections

Replace all `rememberSaveable` slider floats + single note with ViewModel state.
Add `RecoveryViewModel` and `ModelManagerViewModel` as composable parameters.

Updated slider set (label | subtext prompt | low label | high label):

| Field | User-facing question | Low | High |
|---|---|---|---|
| Craving Intensity | "How strong were your urges to use today?" | Quiet | Loud |
| Mood | "Overall, how would you rate your mood today?" | Low | Steady |
| Sleep Quality | "How well did you sleep last night?" | Restless | Rested |
| Stress Level | "How stressed did you feel today?" | Calm | Overloaded |
| Social Connection | "How connected to others did you feel today?" | Isolated | Connected |
| Self-Efficacy | "How confident are you in staying sober tomorrow?" | Doubtful | Confident |

Add **Sleep Hours** `OutlinedTextField` between the sliders card and the triggers card:
- `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)`
- Label: `"Hours slept last night"`
- Single line, max 4 chars
- Shows `sleepHoursError` inline if set

Update trigger chips to Marlatt's 8 taxonomy keys (display labels mapped from constants):
```
"Interpersonal conflict" | "Social pressure to use" | "Substance cues (places/people)"
"Financial stress" | "Work stress" | "Physical pain" | "Positive event / celebration" | "None"
```

Replace single note field with **two** `OutlinedTextField`s:
- `"What was hardest today?"` — placeholder: `"A situation, feeling, or moment that felt risky or difficult..."`
- `"What helped today?"` — placeholder: `"A coping strategy, person, routine, or thought that made things easier..."`

Wire Save button to `recoveryViewModel.saveEntry()`.
Show `Snackbar("Saved")` when `saveStatus == SAVED`.

#### History Screen — live data

Replace `remember { listOf(...) }` with `recoveryViewModel.historyEntries.collectAsState()`.

Map `CheckInEntry → HistoryEntryUi`:
- `date`: format `LocalDate.parse(entry.date)` → `"Today"` / `"Yesterday"` / `"Mon, Apr 28"`
- `headline`: derived from the two most extreme values, e.g. `"Craving-heavy day, better sleep"`
  (craving ≥ 7 → "Craving-heavy", mood ≤ 3 → "Low mood", etc.)
- `riskLabel`: composite of craving + stress:
    - avg ≤ 3 → `"Lower"`, 4–6 → `"Moderate"`, ≥ 7 → `"Elevated"`
- `tags`: `entry.triggers` mapped to display labels; prepend `"✓ helped"` if `helpedToday` is non-empty
- `note`: truncated `hardestToday` (first 80 chars)

#### Insights Screen — dynamic rendering

`RecoveryInsightsScreen` accepts `InsightsViewModel` and `ModelManagerViewModel`.
Calls `insightsViewModel.checkModelAvailability(modelManagerViewModel)` on first composition.

**Section 1 — Early Signals** (always shown if earlySignals is non-empty):
- Title: `"What's Shifting"`
- Bullet list of strings from `insightsUiState.earlySignals`
- If empty + < 4 entries: `"Keep logging — early signals appear after a few check-ins."`

**Section 2 — Patterns** (Gemma-powered):
Only shown when `gemmaStatus is Done`. Two `InsightCard`s from `done.patterns`.
Each card shows `item.text` (body) and `item.evidence` (small caption, e.g. "Based on 4 similar days").

**Section 3 — Protective Factors** (Gemma-powered):
Same structure as Patterns but from `done.protective`. Green accent tone.

**Section 4 — Consistency** (always shown):
- Title: `"Your Consistency"`
- Shows `consistency.currentStreak`, `consistency.missedThisWeek`, `consistency.message`

**Section 5 — Evidence** (shown when `gemmaStatus is Done`):
- Title: `"Why the app says this"`
- Shows last 3 entries that fed the insights as `EvidenceRow`s
- Format: `"May 1 — Craving: 7, Stress: 8, Sleep: 3h / Work stress, conflict"`

**Generate Insights button** (shown when `gemmaStatus is Idle`):
- Primary button: `"Generate Insights"`
- Subtitle: `"Uses Gemma 4 E2B on your device. Nothing leaves your phone."`

**Loading state** (`gemmaStatus is Loading`):
- Replaces button with `CircularProgressIndicator` + streaming text preview (partial output)
- Message: `"Gemma is analyzing your entries..."`

**NoModel state** (`gemmaStatus is NoModel`):
- Outlined card: `"Load Gemma 4 E2B in the main screen, then come back to generate insights."`
- Button: `"Go to Models"` (navigate back to home)

**NotEnoughData state** (`gemmaStatus is NotEnoughData`):
- Outlined card: `"Log at least 5 check-ins to unlock AI-powered pattern insights."`
- Shows entry count progress, e.g. `"2 of 5 check-ins logged"`

---

## Implementation Order

1. `checkin.proto` → run `./gradlew generateProto` to generate Java classes
2. `CheckInSerializer.kt`
3. `CheckInRepository.kt` (interface + `DefaultCheckInRepository`)
4. `AppModule.kt` — add three providers
5. `RecoveryViewModel.kt` — log form state + save + history
6. Wire Log Screen in `RecoveryApp.kt` — fields, sliders, triggers, save button
7. Wire History Screen in `RecoveryApp.kt` — live entries from ViewModel
8. `InsightsEngine.kt` — rule-based computations + prompt builder (no Android deps, fully testable)
9. `InsightsViewModel.kt` — Gemma integration
10. Wire Insights Screen in `RecoveryApp.kt` — all 5 sections + loading/error states

---

## Verification

1. **Build**: `./gradlew assembleDebug` — clean compile after proto generation
2. **Persistence**: Save a check-in, force-kill app, reopen — entry appears in History tab
3. **Upsert**: Save a second check-in same calendar day — History shows one entry, not two
4. **Sleep hours validation**: Type `"25"` or `"abc"` — inline error shown; `"6.5"` accepted
5. **Early signals**: After 5+ entries, Insights tab shows signal strings without tapping any button
6. **Consistency**: Streak and missed-days numbers update after each save
7. **Gemma — no model**: With no model loaded, Insights tab shows "Load Gemma 4 E2B" CTA
8. **Gemma — not enough data**: With 3 entries and model loaded, shows entry count progress card
9. **Gemma — generation**: With ≥5 entries + Gemma 4 E2B loaded, tap "Generate Insights" →
   streaming text appears → Done state shows 2 pattern cards + 2 protective factor cards with evidence
10. **Gemma — isolation**: Verify chat conversation history is unaffected after generating insights
    (send a chat message before and after; prior message still visible in chat)clear