package com.google.ai.edge.gallery.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.CheckInRepository
import com.google.ai.edge.gallery.proto.CheckInEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }

enum class EntryScenario {
  STEADY_PROGRESS,
  DOWNWARD_SPIRAL,
  VOLATILE_ROLLERCOASTER
}

data class LogUiState(
  val date: String = "", // empty means today's new entry
  val cravingIntensity: Int = 5,
  val mood: Int = 5,
  val stressLevel: Int = 5,
  val socialConnection: Int = 5,
  val selfEfficacy: Int = 5,
  val selectedTriggers: Set<String> = emptySet(),
  val reflection: String = "",
  val saveStatus: SaveStatus = SaveStatus.IDLE,
)

@HiltViewModel
class RecoveryViewModel @Inject constructor(
  private val checkInRepository: CheckInRepository,
) : ViewModel() {

  private val _logUiState = MutableStateFlow(LogUiState())
  val logUiState: StateFlow<LogUiState> = _logUiState.asStateFlow()

  private val _historyEntries = MutableStateFlow<List<CheckInEntry>>(emptyList())
  val historyEntries: StateFlow<List<CheckInEntry>> = _historyEntries.asStateFlow()

  init {
    refreshHistory()
    checkTodayEntry()
  }

  fun refreshHistory() {
    viewModelScope.launch {
      _historyEntries.value = checkInRepository.getAllEntries()
    }
  }

  private fun checkTodayEntry() {
    viewModelScope.launch {
      val today = LocalDate.now().toString()
      val entries = checkInRepository.getAllEntries()
      val todayEntry = entries.find { it.date == today }
      if (todayEntry != null) {
        populateForm(todayEntry)
      }
    }
  }

  fun populateForm(entry: CheckInEntry) {
    _logUiState.update {
      it.copy(
        date = entry.date,
        cravingIntensity = entry.cravingIntensity,
        mood = entry.mood,
        stressLevel = entry.stressLevel,
        socialConnection = entry.socialConnection,
        selfEfficacy = entry.selfEfficacy,
        selectedTriggers = entry.triggersList.toSet(),
        reflection = entry.reflection,
        saveStatus = SaveStatus.IDLE
      )
    }
  }

  fun resetForm() {
    _logUiState.value = LogUiState()
    checkTodayEntry() // restore today if it exists
  }

  fun updateCravingIntensity(v: Int) = _logUiState.update { it.copy(cravingIntensity = v) }
  fun updateMood(v: Int) = _logUiState.update { it.copy(mood = v) }
  fun updateStressLevel(v: Int) = _logUiState.update { it.copy(stressLevel = v) }
  fun updateSocialConnection(v: Int) = _logUiState.update { it.copy(socialConnection = v) }
  fun updateSelfEfficacy(v: Int) = _logUiState.update { it.copy(selfEfficacy = v) }
  fun updateReflection(v: String) = _logUiState.update { it.copy(reflection = v) }

  fun toggleTrigger(key: String) {
    _logUiState.update { state ->
      val newTriggers = state.selectedTriggers.toMutableSet()
      if (key == TriggerKeys.NONE) {
        // If "None" is selected, clear everything else and just keep "None"
        newTriggers.clear()
        newTriggers.add(TriggerKeys.NONE)
      } else {
        // If something else is selected, remove "None" first
        newTriggers.remove(TriggerKeys.NONE)
        if (newTriggers.contains(key)) {
          newTriggers.remove(key)
        } else {
          newTriggers.add(key)
        }
        // If after toggling everything is empty, default back to "None"
        if (newTriggers.isEmpty()) {
          newTriggers.add(TriggerKeys.NONE)
        }
      }
      state.copy(selectedTriggers = newTriggers)
    }
  }

  fun saveEntry() {
    _logUiState.update { it.copy(saveStatus = SaveStatus.SAVING) }

    viewModelScope.launch {
      val state = _logUiState.value
      val entryDate = if (state.date.isEmpty()) LocalDate.now().toString() else state.date
      
      val entry = CheckInEntry.newBuilder()
        .setDate(entryDate)
        .setCravingIntensity(state.cravingIntensity)
        .setMood(state.mood)
        .setStressLevel(state.stressLevel)
        .setSocialConnection(state.socialConnection)
        .setSelfEfficacy(state.selfEfficacy)
        .addAllTriggers(state.selectedTriggers)
        .setReflection(state.reflection)
        .setTimestampMs(System.currentTimeMillis())
        .build()

      checkInRepository.addOrReplaceEntry(entry)
      _logUiState.update { it.copy(saveStatus = SaveStatus.SAVED) }
      refreshHistory()

      delay(1500)
      _logUiState.update { it.copy(saveStatus = SaveStatus.IDLE) }
    }
  }

  fun getEntryCount(): Int = checkInRepository.getEntryCount()

  fun seedMockData(scenario: EntryScenario = EntryScenario.STEADY_PROGRESS) {
    viewModelScope.launch {
      val now = LocalDate.now()
      checkInRepository.clearAllEntries() // Start fresh for the scenario

      for (i in 0..30) {
        val date = now.minusDays(i.toLong()).toString()
        val timestamp = System.currentTimeMillis() - (i * 86400000L)

        val entry = when (scenario) {
          EntryScenario.STEADY_PROGRESS -> generateSteadyProgressDay(date, i, timestamp)
          EntryScenario.DOWNWARD_SPIRAL -> generateDownwardSpiralDay(date, i, timestamp)
          EntryScenario.VOLATILE_ROLLERCOASTER -> generateVolatileDay(date, i, timestamp)
        }
        checkInRepository.addOrReplaceEntry(entry)
      }
      refreshHistory()
    }
  }

  private fun generateSteadyProgressDay(date: String, dayIndex: Int, timestamp: Long): CheckInEntry {
    // Trend: Improving over time (dayIndex 30 was worse than dayIndex 0)
    val progressFactor = (30 - dayIndex).toFloat() / 30f // 1.0 today, 0.0 a month ago
    return CheckInEntry.newBuilder()
      .setDate(date)
      .setCravingIntensity((6 - (4 * progressFactor)).toInt().coerceIn(1, 10))
      .setMood((4 + (5 * progressFactor)).toInt().coerceIn(1, 10))
      .setStressLevel((7 - (4 * progressFactor)).toInt().coerceIn(1, 10))
      .setSocialConnection((3 + (6 * progressFactor)).toInt().coerceIn(1, 10))
      .setSelfEfficacy((4 + (5 * progressFactor)).toInt().coerceIn(1, 10))
      .addAllTriggers(if (dayIndex > 20) listOf(TriggerKeys.WORK_STRESS) else listOf(TriggerKeys.POSITIVE_CELEBRATION))
      .setReflection("Feeling more stable as the weeks go by. My social support is really helping me stay focused.")
      .setTimestampMs(timestamp)
      .build()
  }

  private fun generateDownwardSpiralDay(date: String, dayIndex: Int, timestamp: Long): CheckInEntry {
    // Trend: Getting worse recently (dayIndex 0 is worse than dayIndex 30)
    val decayFactor = (30 - dayIndex).toFloat() / 30f // 1.0 today, 0.0 a month ago
    return CheckInEntry.newBuilder()
      .setDate(date)
      .setCravingIntensity((2 + (7 * decayFactor)).toInt().coerceIn(1, 10))
      .setMood((8 - (6 * decayFactor)).toInt().coerceIn(1, 10))
      .setStressLevel((2 + (7 * decayFactor)).toInt().coerceIn(1, 10))
      .setSocialConnection((8 - (6 * decayFactor)).toInt().coerceIn(1, 10))
      .setSelfEfficacy((9 - (7 * decayFactor)).toInt().coerceIn(1, 10))
      .addAllTriggers(if (dayIndex < 10) listOf(TriggerKeys.SUBSTANCE_CUES, TriggerKeys.SOCIAL_PRESSURE) else listOf(TriggerKeys.NONE))
      .setReflection("It's getting harder lately. I've been isolating myself and the cravings are becoming much more frequent.")
      .setTimestampMs(timestamp)
      .build()
  }

  private fun generateVolatileDay(date: String, dayIndex: Int, timestamp: Long): CheckInEntry {
    // High variability based on day of week or random swings
    val isWeekend = dayIndex % 7 == 0 || dayIndex % 7 == 1
    val swing = if (dayIndex % 2 == 0) 1 else -1
    return CheckInEntry.newBuilder()
      .setDate(date)
      .setCravingIntensity(if (isWeekend) 8 else (4 + (3 * swing)))
      .setMood(if (isWeekend) 3 else (6 + (2 * swing)))
      .setStressLevel(if (isWeekend) 7 else (5 + (4 * swing)))
      .setSocialConnection(if (isWeekend) 2 else 7)
      .setSelfEfficacy(if (isWeekend) 4 else 8)
      .addAllTriggers(if (isWeekend) listOf(TriggerKeys.SOCIAL_PRESSURE) else listOf(TriggerKeys.WORK_STRESS))
      .setReflection("Every day feels like a coin toss. One day I'm fine, the next I'm struggling with intense pressure.")
      .setTimestampMs(timestamp)
      .build()
  }
}
