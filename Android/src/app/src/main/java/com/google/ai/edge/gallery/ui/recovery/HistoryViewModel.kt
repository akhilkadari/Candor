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
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class DayColor { GOOD, WATCH, HIGH_RISK, EMPTY }

data class DayData(
  val date: LocalDate,
  val color: DayColor,
  val maxCraving: Int?,          // null if no entry that day
  val dayLabel: String,          // "M", "T", "W", etc.
)

data class HistoryUiState(
  val calendarDays: List<DayData> = emptyList(),   // 14 days, oldest first
  val recentEntries: List<Entry> = emptyList(),
  val streak: Int = 0,
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

      val today = LocalDate.now(ZoneId.systemDefault())
      val zone = ZoneId.systemDefault()

      // Group entries by local calendar day
      val byDay: Map<LocalDate, List<Entry>> = entries.groupBy { entry ->
        java.time.Instant.ofEpochMilli(entry.timestamp)
          .atZone(zone)
          .toLocalDate()
      }

      // Build 14-day calendar (oldest → newest)
      val calendarDays = (13 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val dayEntries = byDay[date]
        val maxCraving = dayEntries?.maxOfOrNull { it.cravingIntensity }
        DayData(
          date = date,
          color = cravingToColor(maxCraving),
          maxCraving = maxCraving,
          dayLabel = date.dayOfWeek.name.take(1),  // "M", "T", "W", etc.
        )
      }

      // Streak: count consecutive days ending today that have an entry
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

  private fun cravingToColor(craving: Int?): DayColor = when {
    craving == null    -> DayColor.EMPTY
    craving <= 4       -> DayColor.GOOD
    craving <= 7       -> DayColor.WATCH
    else               -> DayColor.HIGH_RISK
  }
}
