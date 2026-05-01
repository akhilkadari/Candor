package com.google.ai.edge.gallery.ui.recovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.recovery.Entry
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val ColorGood     = Color(0xFF4CAF82)
private val ColorWatch    = Color(0xFFD4953A)
private val ColorHighRisk = Color(0xFFD45A4A)
private val ColorEmpty    = Color(0xFF2C2C2E)

private fun DayColor.toColor(): Color = when (this) {
  DayColor.GOOD      -> ColorGood
  DayColor.WATCH     -> ColorWatch
  DayColor.HIGH_RISK -> ColorHighRisk
  DayColor.EMPTY     -> ColorEmpty
}

@Composable
fun HistoryScreen(
  modifier: Modifier = Modifier,
  viewModel: HistoryViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsState()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // ── Header ──────────────────────────────────────────────────────────
    item {
      Spacer(Modifier.height(24.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column {
          Text(
            text = "YOUR JOURNEY",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
          )
          Text(
            text = "History",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
          )
        }

        if (state.streak > 0) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
              contentAlignment = Alignment.Center,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔥", fontSize = 16.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                  text = "${state.streak}",
                  color = MaterialTheme.colorScheme.onSurface,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            Text(
              text = "day streak",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp,
              modifier = Modifier.padding(top = 2.dp),
            )
          }
        }
      }
    }

    // ── Calendar heatmap ─────────────────────────────────────────────────
    item {
      CalendarHeatmap(
        days = state.calendarDays,
        selectedDate = state.selectedDate,
        onDayClick = { viewModel.selectDay(it) },
      )
    }

    // ── Day detail panel ─────────────────────────────────────────────────
    item {
      AnimatedVisibility(
        visible = state.selectedDate != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        state.selectedDate?.let { date ->
          DayDetailPanel(
            date = date,
            entries = state.selectedDayEntries,
          )
        }
      }
    }

    // ── Recent entries ────────────────────────────────────────────────────
    if (state.recentEntries.isNotEmpty()) {
      item {
        Text(
          text = "RECENT ENTRIES",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          letterSpacing = 1.sp,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
      items(state.recentEntries) { entry ->
        EntryRow(entry = entry)
      }
    } else {
      item {
        Text(
          text = "No entries yet. Start logging to see your history.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 14.sp,
          modifier = Modifier.padding(top = 24.dp),
        )
      }
    }

    item { Spacer(Modifier.height(32.dp)) }
  }
}

@Composable
internal fun CalendarHeatmap(
  days: List<DayData>,
  selectedDate: LocalDate?,
  onDayClick: (LocalDate) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    WeekRow(days = days.take(7), selectedDate = selectedDate, onDayClick = onDayClick)
    WeekRow(days = days.drop(7), selectedDate = selectedDate, onDayClick = onDayClick)

    Spacer(Modifier.height(4.dp))
    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LegendItem(color = ColorGood,     label = "good")
      LegendItem(color = ColorWatch,    label = "watch")
      LegendItem(color = ColorHighRisk, label = "high risk")
    }
  }
}

@Composable
private fun WeekRow(
  days: List<DayData>,
  selectedDate: LocalDate?,
  onDayClick: (LocalDate) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    days.forEach { day ->
      val isSelected = day.date == selectedDate
      val isClickable = day.color != DayColor.EMPTY

      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = day.dayLabel,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
        )
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(day.color.toColor())
            .then(
              if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
              else Modifier
            )
            .then(
              if (isClickable) Modifier.clickable { onDayClick(day.date) }
              else Modifier
            ),
        )
      }
    }
  }
}

@Composable
internal fun DayDetailPanel(
  date: LocalDate,
  entries: List<Entry>,
) {
  val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    shape = RoundedCornerShape(14.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = dateLabel,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
      )

      if (entries.isEmpty()) {
        Text(
          text = "No entry logged for this day.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 13.sp,
        )
      } else {
        entries.forEach { entry ->
          if (entries.size > 1) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
          DayEntryDetail(entry = entry)
        }
      }
    }
  }
}

@Composable
private fun DayEntryDetail(entry: Entry) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      MetricChip(label = "Craving", value = entry.cravingIntensity)
      MetricChip(label = "Mood", value = entry.mood)
      MetricChip(label = "Stress", value = entry.stressLevel)
      MetricChip(label = "Social", value = entry.socialConnection)
    }

    if (entry.note.isNotBlank()) {
      Text(
        text = entry.note,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        maxLines = 3,
      )
    }
  }
}

@Composable
private fun MetricChip(label: String, value: Int) {
  val chipColor = when {
    label == "Craving" && value >= 8 -> ColorHighRisk.copy(alpha = 0.2f)
    label == "Craving" && value >= 5 -> ColorWatch.copy(alpha = 0.2f)
    label == "Craving"               -> ColorGood.copy(alpha = 0.2f)
    else                             -> MaterialTheme.colorScheme.surfaceContainerHighest
  }
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(chipColor)
      .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = value.toString(),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
      )
    }
  }
}

@Composable
private fun LegendItem(color: Color, label: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color),
    )
    Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
  }
}

@Composable
private fun EntryRow(entry: Entry) {
  val dateStr = SimpleDateFormat("MMM d", Locale.US).format(Date(entry.timestamp))
  val moodWord = when {
    entry.mood >= 7 -> "mood good"
    entry.mood >= 4 -> "mood okay"
    else            -> "mood rough"
  }
  val dotColor = when {
    entry.cravingIntensity <= 4 -> ColorGood
    entry.cravingIntensity <= 7 -> ColorWatch
    else                        -> ColorHighRisk
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(14.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = dateStr,
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "$moodWord · craving ${entry.cravingIntensity}",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 13.sp,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
      Box(
        modifier = Modifier
          .size(12.dp)
          .clip(CircleShape)
          .background(dotColor),
      )
    }
  }
}
