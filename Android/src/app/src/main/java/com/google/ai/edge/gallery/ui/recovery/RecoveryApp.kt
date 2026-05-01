package com.google.ai.edge.gallery.ui.recovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.proto.CheckInEntry
import com.google.ai.edge.gallery.ui.home.SettingsDialog
import com.google.ai.edge.gallery.ui.llmchat.LlmChatScreen
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class RecoveryTab(val label: String, val badge: String) {
  LOG("Log", "L"),
  INSIGHTS("Insights", "I"),
  HISTORY("History", "H"),
  AI("AI", "G"),
}

private data class InsightSection(
  val title: String,
  val summary: String,
  val points: List<String>,
  val tone: Color,
)

@Composable
fun RecoveryApp(
  modelManagerViewModel: ModelManagerViewModel,
  modifier: Modifier = Modifier,
  recoveryViewModel: RecoveryViewModel = hiltViewModel(),
  insightsViewModel: InsightsViewModel = hiltViewModel()
) {
  var selectedTab by rememberSaveable { mutableStateOf(RecoveryTab.LOG) }
  var showAiChat by rememberSaveable { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  BackHandler(enabled = showAiChat) { showAiChat = false }

  val logUiState by recoveryViewModel.logUiState.collectAsState()

  LaunchedEffect(logUiState.saveStatus) {
    if (logUiState.saveStatus == SaveStatus.SAVED) {
      snackbarHostState.showSnackbar("Daily Check-In Saved")
    }
  }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (!showAiChat) {
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
      }
    },
  ) { innerPadding ->
    when (selectedTab) {
      RecoveryTab.LOG -> RecoveryLogScreen(
        contentPadding = innerPadding,
        viewModel = recoveryViewModel,
      )
      RecoveryTab.INSIGHTS -> RecoveryInsightsScreen(
        contentPadding = innerPadding,
        viewModel = insightsViewModel,
        modelManagerViewModel = modelManagerViewModel
      )
      RecoveryTab.HISTORY -> RecoveryHistoryScreen(
        contentPadding = innerPadding,
        viewModel = recoveryViewModel
      )
      RecoveryTab.AI -> {
        if (showAiChat) {
          LlmChatScreen(
            modelManagerViewModel = modelManagerViewModel,
            navigateUp = { showAiChat = false },
          )
        } else {
          AiLauncherScreen(
            contentPadding = innerPadding,
            onOpenChat = { showAiChat = true },
            onOpenSettings = { showSettingsDialog = true },
          )
        }
      }
    }
  }

  if (showSettingsDialog) {
    SettingsDialog(
      curThemeOverride = modelManagerViewModel.readThemeOverride(),
      modelManagerViewModel = modelManagerViewModel,
      onDismissed = { showSettingsDialog = false },
    )
  }
}

@Composable
private fun AiLauncherScreen(
  contentPadding: PaddingValues,
  onOpenChat: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(contentPadding)
        .padding(horizontal = 24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = "Gemma 4",
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = "On-Device AI Chat",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
    )
    Button(
      onClick = onOpenChat,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Open Gemma 4 E2B Chat")
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
      onClick = onOpenSettings,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(
        imageVector = Icons.Rounded.Settings,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text("Settings")
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RecoveryLogScreen(
  contentPadding: PaddingValues,
  viewModel: RecoveryViewModel,
) {
  val state by viewModel.logUiState.collectAsState()

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
        body = "Track recovery-relevant signals now. Your entries are private and stay on your device.",
      )
    }

    item {
      SectionCard(
        title = "How are you feeling today?",
        subtitle = "Use the sliders to log intensity today."
      ) {
        TriggerSlider(
          label = "Craving intensity",
          description = "How strong were your urges to use today?",
          value = state.cravingIntensity.toFloat(),
          valueLabel = state.cravingIntensity.toString(),
          lowLabel = "Quiet",
          highLabel = "Loud",
          onValueChange = { viewModel.updateCravingIntensity(it.toInt()) },
        )
        TriggerSlider(
          label = "Mood",
          description = "Overall, how would you rate your mood today?",
          value = state.mood.toFloat(),
          valueLabel = state.mood.toString(),
          lowLabel = "Low",
          highLabel = "Steady",
          onValueChange = { viewModel.updateMood(it.toInt()) },
        )
        TriggerSlider(
          label = "Sleep quality",
          description = "How well did you sleep last night?",
          value = state.sleepQuality.toFloat(),
          valueLabel = state.sleepQuality.toString(),
          lowLabel = "Restless",
          highLabel = "Rested",
          onValueChange = { viewModel.updateSleepQuality(it.toInt()) },
        )
        TriggerSlider(
          label = "Stress level",
          description = "How stressed did you feel today?",
          value = state.stressLevel.toFloat(),
          valueLabel = state.stressLevel.toString(),
          lowLabel = "Calm",
          highLabel = "Overloaded",
          onValueChange = { viewModel.updateStressLevel(it.toInt()) },
        )
        TriggerSlider(
          label = "Social connection",
          description = "How connected to others did you feel today?",
          value = state.socialConnection.toFloat(),
          valueLabel = state.socialConnection.toString(),
          lowLabel = "Isolated",
          highLabel = "Connected",
          onValueChange = { viewModel.updateSocialConnection(it.toInt()) },
        )
        TriggerSlider(
          label = "Self-efficacy",
          description = "How confident are you in staying sober tomorrow?",
          value = state.selfEfficacy.toFloat(),
          valueLabel = state.selfEfficacy.toString(),
          lowLabel = "Doubtful",
          highLabel = "Confident",
          onValueChange = { viewModel.updateSelfEfficacy(it.toInt()) },
        )
      }
    }

    item {
      SectionCard(
        title = "Sleep Duration",
        subtitle = "Actual hours of sleep last night."
      ) {
        OutlinedTextField(
          value = state.sleepHours,
          onValueChange = { viewModel.updateSleepHours(it) },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Hours slept last night") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          isError = state.sleepHoursError != null,
          supportingText = state.sleepHoursError?.let { { Text(it) } },
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          ),
        )
      }
    }

    item {
      SectionCard(
        title = "Triggers Today",
        subtitle = "Did anything specific trigger a craving or risk today?"
      ) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          TriggerKeys.all.forEach { key ->
            val selected = key in state.selectedTriggers
            AssistChip(
              onClick = { viewModel.toggleTrigger(key) },
              label = { Text(TriggerKeys.displayLabels[key] ?: key) },
              colors = if (selected) {
                AssistChipDefaults.assistChipColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  labelColor = MaterialTheme.colorScheme.onPrimary
                )
              } else {
                AssistChipDefaults.assistChipColors()
              }
            )
          }
        }
      }
    }

    item {
      SectionCard(
        title = "Reflection",
        subtitle = "Context for the reasoning engine."
      ) {
        OutlinedTextField(
          value = state.hardestToday,
          onValueChange = { viewModel.updateHardestToday(it) },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("What was hardest today?") },
          placeholder = { Text("A situation, feeling, or moment that felt risky or difficult...") },
          minLines = 3,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = state.helpedToday,
          onValueChange = { viewModel.updateHelpedToday(it) },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("What helped today?") },
          placeholder = { Text("A coping strategy, person, routine, or thought that made things easier...") },
          minLines = 3,
        )
      }
    }

    item {
      Button(
        onClick = { viewModel.saveEntry() },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        enabled = state.saveStatus != SaveStatus.SAVING && state.sleepHoursError == null,
        shape = RoundedCornerShape(16.dp)
      ) {
        if (state.saveStatus == SaveStatus.SAVING) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp
          )
        } else {
          Text("Save Daily Check-In", style = MaterialTheme.typography.titleMedium)
        }
      }
    }
  }
}

@Composable
private fun RecoveryInsightsScreen(
  contentPadding: PaddingValues,
  viewModel: InsightsViewModel,
  modelManagerViewModel: ModelManagerViewModel
) {
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.checkModelAvailability(modelManagerViewModel)
    viewModel.refresh()
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
        body = "Analyze your data to find patterns and protective factors using Gemma 4 on-device AI.",
      )
    }

    // Section 1: Early Signals
    if (state.earlySignals.isNotEmpty()) {
      item {
        SectionCard(title = "What's Shifting", subtitle = "Recent trends compared to your baseline.") {
          state.earlySignals.forEach { signal ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
              Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = signal, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
    } else if (state.entryCount < 4) {
      item {
        OutlinedCard(shape = RoundedCornerShape(24.dp)) {
          Text(
            text = "Keep logging — early signals appear after a few check-ins.",
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // Gemma-powered sections
    when (val status = state.gemmaStatus) {
      is GemmaInsightStatus.Done -> {
        // Section 2: Patterns
        item {
          Text("Patterns Detected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(status.patterns) { item ->
          InsightCard(item = item, tone = MaterialTheme.colorScheme.error)
        }

        // Section 3: Protective Factors
        item {
          Text("Protective Factors", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(status.protective) { item ->
          InsightCard(item = item, tone = Color(0xFF317A52))
        }

        // Section 5: Evidence
        item {
          SectionCard(
            title = "Why the app says this",
            subtitle = "Recent entries that fed these insights."
          ) {
            status.evidenceEntries.forEach { entry ->
              val date = try {
                LocalDate.parse(entry.date).format(DateTimeFormatter.ofPattern("MMM d"))
              } catch (e: Exception) {
                entry.date
              }
              val triggerList = entry.triggersList.joinToString(", ") { TriggerKeys.displayLabels[it] ?: it }
              EvidenceRow(
                label = date,
                detail = "Craving: ${entry.cravingIntensity}, Stress: ${entry.stressLevel}, Sleep: ${entry.sleepHours}h / $triggerList"
              )
            }
          }
        }
      }
      is GemmaInsightStatus.Loading -> {
        item {
          Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              CircularProgressIndicator()
              Text("Gemma is analyzing your entries...")
              if (state.streamingText.isNotEmpty()) {
                Text(
                  text = state.streamingText,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 4,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
      is GemmaInsightStatus.NoModel -> {
        item {
          OutlinedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("AI Insights Unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text("Load Gemma 4 E2B in the main screen, then come back to generate insights.", style = MaterialTheme.typography.bodyMedium)
              Button(onClick = { /* Navigate to Home? */ }, modifier = Modifier.fillMaxWidth()) {
                Text("Go to Models")
              }
            }
          }
        }
      }
      is GemmaInsightStatus.NotEnoughData -> {
        item {
          OutlinedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("More Data Needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text("Log at least 5 check-ins to unlock AI-powered pattern insights.", style = MaterialTheme.typography.bodyMedium)
              Text("${state.entryCount} of 5 check-ins logged", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
      is GemmaInsightStatus.Idle, is GemmaInsightStatus.Error -> {
        item {
          Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
          ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Generate AI Insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
              Text("Uses Gemma 4 E2B on your device. Nothing leaves your phone.", style = MaterialTheme.typography.bodyMedium)
              if (status is GemmaInsightStatus.Error) {
                Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
              }
              Button(
                onClick = { viewModel.generateInsights(modelManagerViewModel) },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Generate Insights")
              }
            }
          }
        }
      }
    }

    // Section 4: Consistency
    state.consistency?.let { consistency ->
      item {
        SectionCard(title = "Your Consistency", subtitle = consistency.message) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ConsistencyStat(label = "Streak", value = "${consistency.currentStreak}d")
            ConsistencyStat(label = "This Week", value = "${7 - consistency.missedThisWeek}/7")
            ConsistencyStat(label = "Total", value = consistency.totalEntries.toString())
          }
        }
      }
    }
  }
}

@Composable
private fun InsightCard(item: InsightItem, tone: Color) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(tone))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = item.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
      }
      Text(
        text = item.evidence,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
      )
    }
  }
}

@Composable
private fun ConsistencyStat(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RecoveryHistoryScreen(contentPadding: PaddingValues, viewModel: RecoveryViewModel) {
  val history by viewModel.historyEntries.collectAsState()

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
        title = "A readable timeline of your progress.",
        body = "Review your past entries and see how your recovery journey is evolving.",
      )
    }

    items(history) { entry ->
      HistoryCard(entry = entry)
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HistoryCard(entry: CheckInEntry) {
  val dateLabel = remember(entry.date) {
    try {
      val date = LocalDate.parse(entry.date)
      val today = LocalDate.now()
      when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
      }
    } catch (e: Exception) {
      entry.date
    }
  }

  val riskLabel = remember(entry.cravingIntensity, entry.stressLevel) {
    val avg = (entry.cravingIntensity + entry.stressLevel) / 2f
    when {
      avg >= 7 -> "Elevated"
      avg >= 4 -> "Moderate"
      else -> "Lower"
    }
  }

  val headline = remember(entry) {
    val items = mutableListOf<String>()
    if (entry.cravingIntensity >= 7) items.add("Craving-heavy")
    if (entry.stressLevel >= 7) items.add("High stress")
    if (entry.sleepQuality >= 8) items.add("Good sleep")
    if (entry.mood <= 3) items.add("Low mood")
    if (items.isEmpty()) "Steady day" else items.take(2).joinToString(", ")
  }

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
        Text(text = dateLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(999.dp))
              .background(
                when (riskLabel) {
                  "Elevated" -> MaterialTheme.colorScheme.errorContainer
                  "Moderate" -> Color(0xFFFFF4E1)
                  else -> MaterialTheme.colorScheme.primaryContainer
                }
              )
              .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = "$riskLabel Risk",
            style = MaterialTheme.typography.labelMedium,
            color = when (riskLabel) {
              "Elevated" -> MaterialTheme.colorScheme.onErrorContainer
              "Moderate" -> Color(0xFF7A5600)
              else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
          )
        }
      }

      Text(text = headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

      if (entry.hardestToday.isNotEmpty()) {
        Text(
          text = entry.hardestToday,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entry.triggersList.forEach { key ->
          TagItem(label = TriggerKeys.displayLabels[key] ?: key)
        }
        if (entry.helpedToday.isNotEmpty()) {
          TagItem(label = "✓ helped", color = Color(0xFF317A52).copy(alpha = 0.1f))
        }
      }
    }
  }
}

@Composable
private fun TagItem(label: String, color: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(999.dp))
        .background(color)
        .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
  description: String,
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
      Column(modifier = Modifier.weight(1f)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Text(
        text = valueLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp)
      )
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = 1f..10f, steps = 8)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(text = lowLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(text = highLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

