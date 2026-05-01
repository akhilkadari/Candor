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

data class WeekDayData(
  val date: LocalDate,
  val label: String,
  val color: DayColor,
)

data class HistoryScreenState(
  val weekDays: List<WeekDayData> = emptyList(),
  val selectedDate: LocalDate? = null,
  val displayedEntries: List<CheckInEntry> = emptyList(),
  val streak: Int = 0,
)

@HiltViewModel
class RecoveryViewModel @Inject constructor(
  private val checkInRepository: CheckInRepository,
) : ViewModel() {

  private val _logUiState = MutableStateFlow(LogUiState())
  val logUiState: StateFlow<LogUiState> = _logUiState.asStateFlow()

  private val _historyEntries = MutableStateFlow<List<CheckInEntry>>(emptyList())
  val historyEntries: StateFlow<List<CheckInEntry>> = _historyEntries.asStateFlow()

  private val _historyScreenState = MutableStateFlow(HistoryScreenState())
  val historyScreenState: StateFlow<HistoryScreenState> = _historyScreenState.asStateFlow()

  init {
    refreshHistory()
    checkTodayEntry()
  }

  fun refreshHistory() {
    viewModelScope.launch {
      val all = checkInRepository.getAllEntries()
      _historyEntries.value = all
      rebuildHistoryScreenState(all, _historyScreenState.value.selectedDate)
    }
  }

  fun selectWeekDay(date: LocalDate) {
    val all = _historyEntries.value
    val current = _historyScreenState.value.selectedDate
    val next = if (current == date) null else date
    rebuildHistoryScreenState(all, next)
  }

  private fun rebuildHistoryScreenState(all: List<CheckInEntry>, selectedDate: LocalDate?) {
    val today = LocalDate.now()
    val byDate: Map<String, CheckInEntry> = all.associateBy { it.date }

    val weekDays = (6 downTo 0).map { daysAgo ->
      val date = today.minusDays(daysAgo.toLong())
      val entry = byDate[date.toString()]
      val color = cravingToColor(entry?.cravingIntensity)
      WeekDayData(
        date = date,
        label = date.dayOfWeek.name.take(1),
        color = color,
      )
    }

    var streak = 0
    for (daysAgo in 0..364) {
      val date = today.minusDays(daysAgo.toLong())
      if (byDate.containsKey(date.toString())) streak++ else break
    }

    val displayed = if (selectedDate != null) {
      all.filter { it.date == selectedDate.toString() }
    } else {
      all
    }

    _historyScreenState.value = HistoryScreenState(
      weekDays = weekDays,
      selectedDate = selectedDate,
      displayedEntries = displayed,
      streak = streak,
    )
  }

  private fun cravingToColor(craving: Int?): DayColor = when {
    craving == null -> DayColor.EMPTY
    craving <= 4    -> DayColor.GOOD
    craving <= 7    -> DayColor.WATCH
    else            -> DayColor.HIGH_RISK
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
      // Generate 30 days of diverse data
      for (i in 0..30) {
        val date = now.minusDays(i.toLong()).toString()
        val timestamp = System.currentTimeMillis() - (i * 86400000L)

        // Create different types of days
        val entry = when {
          i % 7 == 0 -> { // Weekly stressful work day
            CheckInEntry.newBuilder()
              .setDate(date)
              .setCravingIntensity(7 + (i % 3))
              .setMood(3 + (i % 2))
              .setStressLevel(8 + (i % 2))
              .setSocialConnection(4)
              .setSelfEfficacy(5)
              .addAllTriggers(listOf(TriggerKeys.WORK_STRESS, TriggerKeys.FINANCIAL_STRESS))
              .setReflection("Mondays are tough. Work deadlines are piling up and I'm feeling the financial pressure. Managed to stay strong but the urge was definitely there.")
              .setTimestampMs(timestamp)
              .build()
          }
          i % 7 == 5 || i % 7 == 6 -> { // Weekends - Social/Substance cues
            val highRisk = i % 14 == 6
            CheckInEntry.newBuilder()
              .setDate(date)
              .setCravingIntensity(if (highRisk) 9 else 4)
              .setMood(if (highRisk) 4 else 8)
              .setStressLevel(if (highRisk) 6 else 2)
              .setSocialConnection(if (highRisk) 2 else 9)
              .setSelfEfficacy(if (highRisk) 3 else 8)
              .addAllTriggers(if (highRisk) listOf(TriggerKeys.SOCIAL_PRESSURE, TriggerKeys.SUBSTANCE_CUES) else listOf(TriggerKeys.POSITIVE_CELEBRATION))
              .setReflection(if (highRisk) "Went to a party where I didn't know many people. Seeing others use was very triggering and I felt out of place." else "Great weekend with family. Felt very supported and didn't even think about using.")
              .setTimestampMs(timestamp)
              .build()
          }
          i % 10 == 3 -> { // Interpersonal conflict day
            CheckInEntry.newBuilder()
              .setDate(date)
              .setCravingIntensity(8)
              .setMood(2)
              .setStressLevel(7)
              .setSocialConnection(1)
              .setSelfEfficacy(4)
              .addAllTriggers(listOf(TriggerKeys.INTERPERSONAL_CONFLICT))
              .setReflection("Had a major argument with a close friend today. Feeling lonely and misunderstood. It's hard not to reach for old coping mechanisms when I'm this upset.")
              .setTimestampMs(timestamp)
              .build()
          }
          else -> { // Normal/Stable days
            CheckInEntry.newBuilder()
              .setDate(date)
              .setCravingIntensity(1 + (i % 3))
              .setMood(6 + (i % 3))
              .setStressLevel(2 + (i % 3))
              .setSocialConnection(7)
              .setSelfEfficacy(8 + (i % 2))
              .addAllTriggers(listOf(TriggerKeys.NONE))
              .setReflection("A steady day. Focused on my routine and felt productive. Cravings were minimal and easily managed.")
              .setTimestampMs(timestamp)
              .build()
          }
        }
        checkInRepository.addOrReplaceEntry(entry)
      }
      refreshHistory()
    }
  }
}
