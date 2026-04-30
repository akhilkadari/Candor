package com.google.ai.edge.gallery.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class RecoveryTab(val label: String, val badge: String) {
  LOG("Log", "L"),
  INSIGHTS("Insights", "I"),
  HISTORY("History", "H"),
}

private data class InsightSection(
  val title: String,
  val summary: String,
  val points: List<String>,
  val tone: Color,
)

private data class HistoryEntryUi(
  val date: String,
  val headline: String,
  val note: String,
  val tags: List<String>,
  val riskLabel: String,
)

@Composable
fun RecoveryApp(modifier: Modifier = Modifier) {
  var selectedTab by rememberSaveable { mutableStateOf(RecoveryTab.LOG) }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
        RecoveryTab.values().forEach { tab ->
          NavigationBarItem(
            selected = selectedTab == tab,
            onClick = { selectedTab = tab },
            icon = {
              Box(
                modifier =
                  Modifier.size(28.dp)
                    .clip(CircleShape)
                    .background(
                      if (selectedTab == tab) {
                        MaterialTheme.colorScheme.primary
                      } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                      }
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = tab.badge,
                  style = MaterialTheme.typography.labelLarge,
                  color =
                    if (selectedTab == tab) {
                      MaterialTheme.colorScheme.onPrimary
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
              }
            },
            label = { Text(tab.label) },
          )
        }
      }
    },
  ) { innerPadding ->
    when (selectedTab) {
      RecoveryTab.LOG -> RecoveryLogScreen(contentPadding = innerPadding)
      RecoveryTab.INSIGHTS -> RecoveryInsightsScreen(contentPadding = innerPadding)
      RecoveryTab.HISTORY -> RecoveryHistoryScreen(contentPadding = innerPadding)
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RecoveryLogScreen(contentPadding: PaddingValues) {
  var mood by rememberSaveable { mutableFloatStateOf(4f) }
  var stress by rememberSaveable { mutableFloatStateOf(6f) }
  var cravings by rememberSaveable { mutableFloatStateOf(5f) }
  var sleepQuality by rememberSaveable { mutableFloatStateOf(4f) }
  var isolation by rememberSaveable { mutableFloatStateOf(7f) }
  var routineStability by rememberSaveable { mutableFloatStateOf(3f) }
  var note by rememberSaveable { mutableStateOf("") }

  val triggerTags =
    remember {
      listOf(
        "Poor sleep",
        "Conflict",
        "Work stress",
        "Isolation",
        "Skipped meeting",
        "Cravings spike",
        "Boredom",
        "Unstructured evening",
      )
    }
  val selectedTags = rememberSaveable { mutableStateOf(setOf("Poor sleep", "Work stress")) }

  LazyColumn(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                MaterialTheme.colorScheme.surfaceContainerLowest,
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f),
              )
          )
        ),
    contentPadding = PaddingValues(
      start = 20.dp,
      end = 20.dp,
      top = contentPadding.calculateTopPadding() + 20.dp,
      bottom = contentPadding.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      RecoveryHeroCard(
        eyebrow = "Daily Check-In",
        title = "Capture today before the hard parts blur together.",
        body =
          "Track recovery-relevant signals now. The data layer can later save these fields exactly as shown here.",
      )
    }

    item {
      SectionCard(title = "Core Triggers", subtitle = "Use the sliders to log intensity today.") {
        TriggerSlider(
          label = "Mood",
          value = mood,
          valueLabel = mood.toInt().toString(),
          lowLabel = "Low",
          highLabel = "Steady",
          onValueChange = { mood = it },
        )
        TriggerSlider(
          label = "Stress",
          value = stress,
          valueLabel = stress.toInt().toString(),
          lowLabel = "Calm",
          highLabel = "Overloaded",
          onValueChange = { stress = it },
        )
        TriggerSlider(
          label = "Cravings",
          value = cravings,
          valueLabel = cravings.toInt().toString(),
          lowLabel = "Quiet",
          highLabel = "Loud",
          onValueChange = { cravings = it },
        )
        TriggerSlider(
          label = "Sleep Quality",
          value = sleepQuality,
          valueLabel = sleepQuality.toInt().toString(),
          lowLabel = "Restless",
          highLabel = "Rested",
          onValueChange = { sleepQuality = it },
        )
        TriggerSlider(
          label = "Isolation",
          value = isolation,
          valueLabel = isolation.toInt().toString(),
          lowLabel = "Connected",
          highLabel = "Cut off",
          onValueChange = { isolation = it },
        )
        TriggerSlider(
          label = "Routine Stability",
          value = routineStability,
          valueLabel = routineStability.toInt().toString(),
          lowLabel = "Scattered",
          highLabel = "Grounded",
          onValueChange = { routineStability = it },
        )
      }
    }

    item {
      SectionCard(
        title = "Known Triggers Today",
        subtitle = "These are temporary UI selections until the database model is connected.",
      ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          triggerTags.forEach { tag ->
            val selected = tag in selectedTags.value
            AssistChip(
              onClick = {
                selectedTags.value =
                  if (selected) {
                    selectedTags.value - tag
                  } else {
                    selectedTags.value + tag
                  }
              },
              label = { Text(tag) },
            )
          }
        }
      }
    }

    item {
      SectionCard(
        title = "Short Reflection",
        subtitle = "Leave room for the note field that later retrieval and insight generation will use.",
      ) {
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          modifier = Modifier.fillMaxWidth(),
          minLines = 5,
          placeholder = {
            Text("What happened today? Any moments that felt risky, protective, or worth remembering?")
          },
        )
      }
    }

    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Ready for save-state hookup",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            text =
              "This footer is intentionally static for now. When the DB work lands, wire the primary action here to persist the slider values, tags, and note.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
          )
          TextButton(onClick = {}) {
            Text("Save Check-In Placeholder")
          }
        }
      }
    }
  }
}

@Composable
private fun RecoveryInsightsScreen(contentPadding: PaddingValues) {
  val sections =
    remember {
      listOf(
        InsightSection(
          title = "Patterns Detected",
          summary = "Recent difficult evenings are clustering around the same conditions.",
          points =
            listOf(
              "High-craving days are appearing after poorer sleep and limited social contact.",
              "Stress spikes are showing up more often on work-heavy evenings.",
            ),
          tone = Color(0xFFD55D3F),
        ),
        InsightSection(
          title = "Early Warning Signs",
          summary = "Signals worth surfacing sooner, before the day feels unrecoverable.",
          points =
            listOf(
              "Three straight days of shortened sleep are often followed by more intense check-ins.",
              "Isolation plus unstructured evenings looks riskier than either factor alone.",
            ),
          tone = Color(0xFFB57921),
        ),
        InsightSection(
          title = "What Changed This Week",
          summary = "The app should make change visible, not just summarize averages.",
          points =
            listOf(
              "Routine stability is trending down while stress is trending up.",
              "Protective routines are being logged less consistently than last week.",
            ),
          tone = Color(0xFF3467C7),
        ),
        InsightSection(
          title = "Protective Factors",
          summary = "Not all insight cards should be risk cards.",
          points =
            listOf(
              "Days with social contact and movement appear steadier.",
              "Structured plans in the evening seem to correspond with lower craving intensity.",
            ),
          tone = Color(0xFF317A52),
        ),
        InsightSection(
          title = "Gentle Next Steps",
          summary = "Short, nonjudgmental guidance that can later be generated by the model.",
          points =
            listOf(
              "Plan one anchored evening routine before tomorrow gets busy.",
              "Reach out to one safe person early if tonight starts to feel isolating.",
            ),
          tone = Color(0xFF7252B8),
        ),
      )
    }

  LazyColumn(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f),
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.16f),
              )
          )
        ),
    contentPadding = PaddingValues(
      start = 20.dp,
      end = 20.dp,
      top = contentPadding.calculateTopPadding() + 20.dp,
      bottom = contentPadding.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      RecoveryHeroCard(
        eyebrow = "Insights",
        title = "Private pattern summaries built for recovery support.",
        body =
          "This screen is shaped around the outputs you described: pattern cards, early warnings, protective factors, evidence, and gentle next steps.",
      )
    }

    items(sections) { section ->
      InsightSectionCard(section = section)
    }

    item {
      SectionCard(
        title = "Evidence Behind The Insights",
        subtitle = "Leave room for the later retrieval layer to attach supporting entries or clusters.",
      ) {
        EvidenceRow(label = "May 1", detail = "High cravings + 4 hours sleep + no meeting")
        EvidenceRow(label = "May 4", detail = "Work stress + isolated evening + skipped dinner")
        EvidenceRow(label = "May 7", detail = "Late work + low mood + steady cravings by 9 PM")
      }
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RecoveryHistoryScreen(contentPadding: PaddingValues) {
  val history =
    remember {
      listOf(
        HistoryEntryUi(
          date = "Today",
          headline = "Stress-heavy afternoon, cravings manageable",
          note = "User note placeholder for the daily check-in summary. Later this will come from storage.",
          tags = listOf("Work stress", "Cravings", "Short sleep"),
          riskLabel = "Moderate",
        ),
        HistoryEntryUi(
          date = "Yesterday",
          headline = "Quiet day, better structure by evening",
          note = "Routine stayed more stable and there was contact with a supportive person.",
          tags = listOf("Protective", "Routine", "Social contact"),
          riskLabel = "Lower",
        ),
        HistoryEntryUi(
          date = "Monday",
          headline = "Isolation rose after work",
          note = "No meeting attendance and energy fell off late in the day.",
          tags = listOf("Isolation", "Skipped meeting", "Fatigue"),
          riskLabel = "Elevated",
        ),
      )
    }

  LazyColumn(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.background,
              )
          )
        ),
    contentPadding = PaddingValues(
      start = 20.dp,
      end = 20.dp,
      top = contentPadding.calculateTopPadding() + 20.dp,
      bottom = contentPadding.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      RecoveryHeroCard(
        eyebrow = "History",
        title = "A readable timeline for later review and evidence.",
        body =
          "This page is structured so the persistence layer can feed it directly: date, summary, note excerpt, risk label, and trigger tags.",
      )
    }

    items(history) { entry ->
      HistoryCard(entry = entry)
    }
  }
}

@Composable
private fun RecoveryHeroCard(eyebrow: String, title: String, body: String) {
  Surface(
    color = Color.Transparent,
    shape = RoundedCornerShape(28.dp),
    shadowElevation = 2.dp,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            brush =
              Brush.linearGradient(
                colors =
                  listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                  )
              )
          )
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = eyebrow.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SectionCard(
  title: String,
  subtitle: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      content()
    }
  }
}

@Composable
private fun TriggerSlider(
  label: String,
  value: Float,
  valueLabel: String,
  lowLabel: String,
  highLabel: String,
  onValueChange: (Float) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(
        text = valueLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = 0f..10f, steps = 9)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(text = lowLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(text = highLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun InsightSectionCard(section: InsightSection) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(12.dp).clip(CircleShape).background(section.tone)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      }
      Text(
        text = section.summary,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      section.points.forEachIndexed { index, point ->
        Row(verticalAlignment = Alignment.Top) {
          Text(
            text = "${index + 1}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = point,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun EvidenceRow(label: String, detail: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(text = "Support", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    Text(
      text = detail,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HistoryCard(entry: HistoryEntryUi) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = entry.date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(999.dp))
              .background(MaterialTheme.colorScheme.secondaryContainer)
              .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = entry.riskLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
      Text(text = entry.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(
        text = entry.note,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entry.tags.forEach { tag ->
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = tag,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
