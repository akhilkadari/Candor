package com.google.ai.edge.gallery.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.recovery.Entry
import com.google.ai.edge.gallery.data.recovery.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class DayColor { GOOD, WATCH, HIGH_RISK, EMPTY }

data class DayData(
  val date: LocalDate,
  val color: DayColor,
  val maxCraving: Int?,
  val dayLabel: String,
)

data class HistoryUiState(
  val calendarDays: List<DayData> = emptyList(),
  val recentEntries: List<Entry> = emptyList(),
  val streak: Int = 0,
  val selectedDate: LocalDate? = null,
  val selectedDayEntries: List<Entry> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
  private val repository: EntryRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HistoryUiState())
  val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun load() {
    viewModelScope.launch {
      val fourteenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(13)
      val entries = repository.getEntriesSince(fourteenDaysAgo)
      val recent = repository.getLastN(20)
      val zone = ZoneId.systemDefault()
      val today = LocalDate.now(zone)

      val byDay: Map<LocalDate, List<Entry>> = entries.groupBy { entry ->
        java.time.Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()
      }

      val calendarDays = (13 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val maxCraving = byDay[date]?.maxOfOrNull { it.cravingIntensity }
        DayData(
          date = date,
          color = cravingToColor(maxCraving),
          maxCraving = maxCraving,
          dayLabel = date.dayOfWeek.name.take(1),
        )
      }

      var streak = 0
      for (daysAgo in 0..364) {
        val date = today.minusDays(daysAgo.toLong())
        if (byDay.containsKey(date)) streak++ else break
      }

      _uiState.value = HistoryUiState(
        calendarDays = calendarDays,
        recentEntries = recent,
        streak = streak,
      )
    }
  }

  fun selectDay(date: LocalDate) {
    val current = _uiState.value
    if (current.selectedDate == date) {
      _uiState.value = current.copy(selectedDate = null, selectedDayEntries = emptyList())
      return
    }
    val zone = ZoneId.systemDefault()
    val dayEntries = current.recentEntries.filter { entry ->
      Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate() == date
    }
    _uiState.value = current.copy(selectedDate = date, selectedDayEntries = dayEntries)
  }

  private fun cravingToColor(craving: Int?): DayColor = when {
    craving == null -> DayColor.EMPTY
    craving <= 4    -> DayColor.GOOD
    craving <= 7    -> DayColor.WATCH
    else            -> DayColor.HIGH_RISK
  }
}
