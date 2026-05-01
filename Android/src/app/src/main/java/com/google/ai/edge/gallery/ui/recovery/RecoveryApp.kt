package com.google.ai.edge.gallery.ui.recovery

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.proto.CheckInEntry
import com.google.ai.edge.gallery.ui.home.SettingsDialog
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class RecoveryTab(val label: String, val icon: ImageVector) {
  LOG("Log", Icons.Rounded.Edit),
  INSIGHTS("Insights", Icons.Rounded.Lightbulb),
  HISTORY("History", Icons.Rounded.CalendarMonth),
}

@Composable
fun RecoveryApp(
  modelManagerViewModel: ModelManagerViewModel,
  modifier: Modifier = Modifier,
  recoveryViewModel: RecoveryViewModel = hiltViewModel(),
  insightsViewModel: InsightsViewModel = hiltViewModel()
) {
  var selectedTab by rememberSaveable { mutableStateOf(RecoveryTab.LOG) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

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
      NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
        RecoveryTab.values().forEach { tab ->
          NavigationBarItem(
            selected = selectedTab == tab,
            onClick = { selectedTab = tab },
            icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
            label = { Text(tab.label) },
          )
        }
      }
    },
  ) { innerPadding ->
    when (selectedTab) {
      RecoveryTab.LOG -> RecoveryLogScreen(
        contentPadding = innerPadding,
        viewModel = recoveryViewModel,
        insightsViewModel = insightsViewModel
      )
      RecoveryTab.INSIGHTS -> RecoveryInsightsScreen(
        contentPadding = innerPadding,
        viewModel = insightsViewModel,
        modelManagerViewModel = modelManagerViewModel
      )
      RecoveryTab.HISTORY -> RecoveryHistoryScreen(
        contentPadding = innerPadding,
        viewModel = recoveryViewModel,
        onEditEntry = { entry ->
          recoveryViewModel.populateForm(entry)
          selectedTab = RecoveryTab.LOG
        }
      )
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
@OptIn(ExperimentalLayoutApi::class)
private fun RecoveryLogScreen(
  contentPadding: PaddingValues,
  viewModel: RecoveryViewModel,
  insightsViewModel: InsightsViewModel
) {
  val uiState by viewModel.logUiState.collectAsState()
  val history by viewModel.historyEntries.collectAsState()
  val isEditing = uiState.date.isNotEmpty()

  val consistency = remember(history) {
    InsightsEngine.computeConsistency(history)
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(
      start = 20.dp, end = 20.dp,
      top = contentPadding.calculateTopPadding(),
      bottom = contentPadding.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      CandorPageHeader(
        subtitle = "Your patterns, decoded.",
        streak = consistency.currentStreak
      )
    }

    item {
      SectionCard(title = "Core Metrics", subtitle = "Use the sliders to log intensity today.") {
        TriggerSlider(
          label = "Craving intensity",
          description = "How strong were your urges to use today?",
          value = uiState.cravingIntensity.toFloat(),
          positiveDirection = false,
          onValueChange = { viewModel.updateCravingIntensity(it.toInt()) }
        )
        TriggerSlider(
          label = "Mood",
          description = "Overall, how would you rate your mood today?",
          value = uiState.mood.toFloat(),
          positiveDirection = true,
          onValueChange = { viewModel.updateMood(it.toInt()) }
        )
        TriggerSlider(
          label = "Stress level",
          description = "How stressed did you feel today?",
          value = uiState.stressLevel.toFloat(),
          positiveDirection = false,
          onValueChange = { viewModel.updateStressLevel(it.toInt()) }
        )
        TriggerSlider(
          label = "Social connection",
          description = "How connected to others did you feel today?",
          value = uiState.socialConnection.toFloat(),
          positiveDirection = true,
          onValueChange = { viewModel.updateSocialConnection(it.toInt()) }
        )
        TriggerSlider(
          label = "Self-efficacy",
          description = "How confident are you in staying sober tomorrow?",
          value = uiState.selfEfficacy.toFloat(),
          positiveDirection = true,
          onValueChange = { viewModel.updateSelfEfficacy(it.toInt()) }
        )
      }
    }

    item {
      SectionCard(title = "Triggers Today", subtitle = "Did any specific situation occur?") {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          TriggerKeys.all.forEach { key ->
            val selected = key in uiState.selectedTriggers
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
      SectionCard(title = "Daily Reflection", subtitle = "General thoughts about your day.") {
        OutlinedTextField(
          value = uiState.reflection,
          onValueChange = { viewModel.updateReflection(it) },
          modifier = Modifier.fillMaxWidth(),
          minLines = 4,
          placeholder = { Text("What happened today? Any moments worth remembering?") }
        )
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isEditing) {
          OutlinedButton(
            onClick = { viewModel.resetForm() },
            modifier = Modifier.weight(1f)
          ) {
            Text("Cancel Edit")
          }
        }

        Button(
          onClick = { viewModel.saveEntry() },
          modifier = if (isEditing) Modifier.weight(1f) else Modifier.fillMaxWidth(),
          enabled = uiState.saveStatus == SaveStatus.IDLE,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          if (uiState.saveStatus == SaveStatus.SAVING) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
          } else {
            Text(if (isEditing) "Update Check-In" else "Save Check-In")
          }
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
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.checkModelAvailability(modelManagerViewModel)
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(
      start = 20.dp, end = 20.dp,
      top = contentPadding.calculateTopPadding(),
      bottom = contentPadding.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      CandorPageHeader(
        subtitle = "Your patterns, decoded."
      )
    }

    uiState.snapshot?.let { snapshot ->
      item {
        SectionCard(
          title = "Latest Analysis",
          subtitle = "Updated ${formatInsightTimestamp(snapshot.generatedAt)} from ${snapshot.entryCount} check-ins."
        ) {
          Text(
            text = "These sections stay saved after generation, so you can come back without re-running analysis every time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item {
      InsightSectionCard(
        title = "Early Signals",
        subtitle = "What has been shifting recently?",
        items = uiState.snapshot?.earlySignals.orEmpty(),
        emptyMessage = "Generate insights to compare your latest check-ins against your recent baseline.",
        color = MaterialTheme.colorScheme.error
      )
    }

    item {
      InsightSectionCard(
        title = "Recurring Patterns",
        subtitle = "What tends to happen together?",
        items = uiState.snapshot?.recurringPatterns.orEmpty(),
        emptyMessage = "Gemma will look for repeated multi-signal combinations across your past entries.",
        color = Color(0xFFD55D3F)
      )
    }

    item {
      InsightSectionCard(
        title = "Protective Factors",
        subtitle = "What actually helps?",
        items = uiState.snapshot?.protectiveFactors.orEmpty(),
        emptyMessage = "Positive correlations will show up here once the app analyzes enough history.",
        color = Color(0xFF317A52)
      )
    }

    item {
      InsightSectionCard(
        title = "Consistency And Behavior",
        subtitle = "How consistent have you been?",
        items = uiState.snapshot?.consistency.orEmpty(),
        emptyMessage = "This section tracks streaks, missed days, and whether gaps line up with harder entries.",
        color = MaterialTheme.colorScheme.primary
      )
    }

    item {
      InsightActionCard(
        status = uiState.gemmaStatus,
        currentAccelerator = uiState.currentAccelerator,
        hasStoredInsights = uiState.snapshot != null,
        onGenerate = { viewModel.generateInsights(modelManagerViewModel) }
      )
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RecoveryHistoryScreen(
  contentPadding: PaddingValues,
  viewModel: RecoveryViewModel,
  onEditEntry: (CheckInEntry) -> Unit
) {
  val history by viewModel.historyEntries.collectAsState()

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(
      start = 20.dp, end = 20.dp,
      top = contentPadding.calculateTopPadding(),
      bottom = contentPadding.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      CandorPageHeader(
        subtitle = "Your journey, day by day."
      )
    }

    if (BuildConfig.DEBUG) {
      item {
        Column {
          Text(
            text = "Seed Test Scenarios",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val scenarios = listOf(
              "Steady" to EntryScenario.STEADY_PROGRESS,
              "Spiral" to EntryScenario.DOWNWARD_SPIRAL,
              "Volatile" to EntryScenario.VOLATILE_ROLLERCOASTER
            )
            scenarios.forEach { (label, scenario) ->
              Button(
                onClick = { viewModel.seedMockData(scenario) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
              ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
    }

    items(history) { entry ->
      HistoryCard(entry = entry, onEdit = { onEditEntry(entry) })
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HistoryCard(entry: CheckInEntry, onEdit: () -> Unit) {
  val dateStr = remember(entry.date) {
    try {
      LocalDate.parse(entry.date).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    } catch (e: Exception) {
      entry.date
    }
  }

  val (badgeLabel, badgeContainer, badgeContent) = remember(entry.cravingIntensity, entry.mood) {
    when {
      entry.cravingIntensity >= 7 ->
        Triple("High Risk", Color(0xFFFFDAD6), Color(0xFFBA1A1A))
      entry.cravingIntensity >= 5 || entry.mood <= 4 ->
        Triple("Watch", Color(0xFFFFEDD5), Color(0xFF7B4200))
      else ->
        Triple("Steady", Color(0xFFCCEDD6), Color(0xFF1B5E20))
    }
  }

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = dateStr,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
          Icon(
            Icons.Rounded.Edit,
            contentDescription = "Edit",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(999.dp),
        color = badgeContainer,
      ) {
        Text(
          text = badgeLabel,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
          style = MaterialTheme.typography.labelMedium,
          color = badgeContent,
          fontWeight = FontWeight.SemiBold,
        )
      }

      if (entry.reflection.isNotEmpty()) {
        Text(
          text = entry.reflection,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        entry.triggersList.forEach { tag ->
          TagItem(label = TriggerKeys.displayLabels[tag] ?: tag)
        }
      }
    }
  }
}

@Composable
private fun InsightSectionCard(
  title: String,
  subtitle: String,
  items: List<InsightItem>,
  emptyMessage: String,
  color: Color
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (items.isEmpty()) {
        Text(
          text = emptyMessage,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      items.forEach { item -> InsightEvidenceRow(item = item) }
    }
  }
}

@Composable
private fun InsightEvidenceRow(item: InsightItem) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = item.text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = item.evidence,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun InsightActionCard(
  status: GemmaInsightStatus,
  currentAccelerator: String?,
  hasStoredInsights: Boolean,
  onGenerate: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = if (hasStoredInsights) "Refresh Insights" else "Generate Insights",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      when (status) {
        GemmaInsightStatus.Loading -> {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Text(
                text = "Gemma 4 E2B is analyzing your saved log history on-device.",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
            currentAccelerator?.let { accel ->
              Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = "Running on: $accel",
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  color = MaterialTheme.colorScheme.onTertiaryContainer
                )
              }
            }
          }
        }
        GemmaInsightStatus.NoModel -> {
          Text(
            text = "Load `gemma-4-E2B-it_qualcomm_sm8750.litertlm` through the Gemma 4 model entry on the main screen to generate or refresh insights on NPU. Saved insights remain visible below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        GemmaInsightStatus.NotEnoughData -> {
          Text(
            text = "Log at least 5 check-ins before generating insights. The page needs enough history to compare recent changes and recurring patterns.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        is GemmaInsightStatus.Error -> {
          Text(
            text = status.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
        is GemmaInsightStatus.Done -> {
          Text(
            text = "Run the model again whenever you want a fresh snapshot after new check-ins.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        GemmaInsightStatus.Idle -> {
          Text(
            text = "This uses your saved history to generate four sections: early signals, recurring patterns, protective factors, and consistency.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      val canGenerate = status !is GemmaInsightStatus.Loading &&
        status !is GemmaInsightStatus.NoModel &&
        status !is GemmaInsightStatus.NotEnoughData

      Button(
        onClick = onGenerate,
        enabled = canGenerate,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (hasStoredInsights) "Refresh From History" else "Generate From History")
      }
    }
  }
}

@Composable
private fun TriggerSlider(
  label: String,
  description: String,
  value: Float,
  positiveDirection: Boolean,
  onValueChange: (Float) -> Unit
) {
  val fraction = ((value - 1f) / 9f).coerceIn(0f, 1f)
  val green = Color(0xFF4CAF50)
  val red = Color(0xFFE53935)
  val trackColor = if (positiveDirection) lerp(red, green, fraction) else lerp(green, red, fraction)

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
        text = value.toInt().toString(),
        style = MaterialTheme.typography.labelLarge,
        color = trackColor
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = 1f..10f,
      steps = 8,
      colors = SliderDefaults.colors(
        activeTrackColor = trackColor,
        thumbColor = trackColor,
      )
    )
  }
}

@Composable
private fun TagItem(label: String) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    modifier = Modifier.padding(vertical = 4.dp)
  ) {
    Text(
      text = label,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun CandorPageHeader(
  subtitle: String,
  streak: Int? = null,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 32.dp, bottom = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(
      text = "Candor",
      style = MaterialTheme.typography.displaySmall,
      fontWeight = FontWeight.ExtraBold,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 32.dp),
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )

    if (streak != null && streak > 0) {
      Spacer(modifier = Modifier.height(8.dp))
      Surface(
        color = Color(0xFFEADDFF), // Light purple
        shape = RoundedCornerShape(999.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(text = "🔥", style = MaterialTheme.typography.labelLarge)
          Text(
            text = "$streak-day streak",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF21005D)
          )
        }
      }
    }
  }
}

private fun formatInsightTimestamp(timestamp: Long): String {
  val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
  return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(formatter)
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      content()
    }
  }
}
