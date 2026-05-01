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
      val updated = if (key in state.selectedTriggers) state.selectedTriggers - key
                    else state.selectedTriggers + key
      state.copy(selectedTriggers = updated)
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

  fun seedMockData() {
    viewModelScope.launch {
      val now = LocalDate.now()
      for (i in 1..21) {
        val date = now.minusDays(i.toLong()).toString()
        // Create some patterns: Every 4th day is high stress/craving, social days are better.
        val isStressful = i % 4 == 0
        val isSocial = i % 3 == 0
        
        val entry = CheckInEntry.newBuilder()
          .setDate(date)
          .setCravingIntensity(if (isStressful) 8 else 2)
          .setMood(if (isStressful) 3 else 7)
          .setStressLevel(if (isStressful) 9 else 3)
          .setSocialConnection(if (isSocial) 8 else 3)
          .setSelfEfficacy(if (isStressful) 4 else 9)
          .addAllTriggers(
            if (isStressful) listOf(TriggerKeys.WORK_STRESS, TriggerKeys.FINANCIAL_STRESS)
            else if (isSocial) listOf(TriggerKeys.POSITIVE_CELEBRATION)
            else listOf(TriggerKeys.NONE)
          )
          .setReflection(
            if (isStressful) "Work was overwhelming today and I'm worried about bills. Felt a lot of pressure to use."
            else if (isSocial) "Spent time with family and felt really supported. Mood is stable."
            else "A quiet, routine day. No major issues."
          )
          .setTimestampMs(System.currentTimeMillis() - (i * 86400000L))
          .build()
        checkInRepository.addOrReplaceEntry(entry)
      }
      refreshHistory()
    }
  }
}
