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
  val cravingIntensity: Int = 5,
  val mood: Int = 5,
  val sleepQuality: Int = 5,
  val stressLevel: Int = 5,
  val socialConnection: Int = 5,
  val selfEfficacy: Int = 5,
  val sleepHours: String = "",
  val sleepHoursError: String? = null,
  val selectedTriggers: Set<String> = emptySet(),
  val hardestToday: String = "",
  val helpedToday: String = "",
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
  }

  fun refreshHistory() {
    viewModelScope.launch {
      _historyEntries.value = checkInRepository.getAllEntries()
    }
  }

  fun updateCravingIntensity(v: Int) = _logUiState.update { it.copy(cravingIntensity = v) }
  fun updateMood(v: Int) = _logUiState.update { it.copy(mood = v) }
  fun updateSleepQuality(v: Int) = _logUiState.update { it.copy(sleepQuality = v) }
  fun updateStressLevel(v: Int) = _logUiState.update { it.copy(stressLevel = v) }
  fun updateSocialConnection(v: Int) = _logUiState.update { it.copy(socialConnection = v) }
  fun updateSelfEfficacy(v: Int) = _logUiState.update { it.copy(selfEfficacy = v) }
  fun updateHardestToday(v: String) = _logUiState.update { it.copy(hardestToday = v) }
  fun updateHelpedToday(v: String) = _logUiState.update { it.copy(helpedToday = v) }

  fun updateSleepHours(v: String) {
    val error = when {
      v.isEmpty() -> null
      v.toFloatOrNull() == null -> "Enter a valid number"
      v.toFloat() !in 0f..24f -> "Must be between 0 and 24"
      else -> null
    }
    _logUiState.update { it.copy(sleepHours = v, sleepHoursError = error) }
  }

  fun toggleTrigger(key: String) {
    _logUiState.update { state ->
      val updated = if (key in state.selectedTriggers) state.selectedTriggers - key
                    else state.selectedTriggers + key
      state.copy(selectedTriggers = updated)
    }
  }

  fun saveEntry() {
    val state = _logUiState.value
    if (state.sleepHoursError != null) return

    _logUiState.update { it.copy(saveStatus = SaveStatus.SAVING) }

    viewModelScope.launch {
      val entry = CheckInEntry.newBuilder()
        .setDate(LocalDate.now().toString())
        .setCravingIntensity(state.cravingIntensity)
        .setMood(state.mood)
        .setSleepQuality(state.sleepQuality)
        .setStressLevel(state.stressLevel)
        .setSocialConnection(state.socialConnection)
        .setSelfEfficacy(state.selfEfficacy)
        .setSleepHours(state.sleepHours.toFloatOrNull() ?: 0f)
        .addAllTriggers(state.selectedTriggers)
        .setHardestToday(state.hardestToday)
        .setHelpedToday(state.helpedToday)
        .setTimestampMs(System.currentTimeMillis())
        .build()

      checkInRepository.addOrReplaceEntry(entry)
      _logUiState.update { it.copy(saveStatus = SaveStatus.SAVED) }
      refreshHistory()

      delay(2000)
      _logUiState.update {
        LogUiState(saveStatus = SaveStatus.IDLE)
      }
    }
  }

  fun getEntryCount(): Int = checkInRepository.getEntryCount()
}
