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
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.proto.CheckInEntry
import com.google.ai.edge.gallery.ui.home.SettingsDialog
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class RecoveryTab(val label: String, val badge: String) {
  LOG("Log", "L"),
  INSIGHTS("Insights", "I"),
  HISTORY("History", "H"),
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
      RecoveryTab.LOG -> RecoveryLogScreen(
        contentPadding = innerPadding,
        viewModel = recoveryViewModel
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
  viewModel: RecoveryViewModel
) {
  val uiState by viewModel.logUiState.collectAsState()
  val isEditing = uiState.date.isNotEmpty()

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(
      Brush.verticalGradient(
        colors = listOf(
          MaterialTheme.colorScheme.surfaceContainerLowest,
          MaterialTheme.colorScheme.background,
          MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f),
        )
      )
    ),
    contentPadding = PaddingValues(
      start = 20.dp, end = 20.dp,
      top = contentPadding.calculateTopPadding() + 20.dp,
      bottom = contentPadding.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      val eyebrow = if (isEditing) "Edit Check-In: ${uiState.date}" else "Daily Check-In"
      RecoveryHeroCard(
        eyebrow = eyebrow,
        title = "Capture today before the hard parts blur together.",
        body = "Track recovery-relevant signals now. Your insights will update automatically.",
      )
    }

    item {
      SectionCard(title = "Core Metrics", subtitle = "Use the sliders to log intensity today.") {
        TriggerSlider(
          label = "Craving intensity",
          description = "How strong were your urges to use today?",
          value = uiState.cravingIntensity.toFloat(),
          onValueChange = { viewModel.updateCravingIntensity(it.toInt()) }
        )
        TriggerSlider(
          label = "Mood",
          description = "Overall, how would you rate your mood today?",
          value = uiState.mood.toFloat(),
          onValueChange = { viewModel.updateMood(it.toInt()) }
        )
        TriggerSlider(
          label = "Stress level",
          description = "How stressed did you feel today?",
          value = uiState.stressLevel.toFloat(),
          onValueChange = { viewModel.updateStressLevel(it.toInt()) }
        )
        TriggerSlider(
          label = "Social connection",
          description = "How connected to others did you feel today?",
          value = uiState.socialConnection.toFloat(),
          onValueChange = { viewModel.updateSocialConnection(it.toInt()) }
        )
        TriggerSlider(
          label = "Self-efficacy",
          description = "How confident are you in staying sober tomorrow?",
          value = uiState.selfEfficacy.toFloat(),
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
            val label = if (isEditing) "Update Check-In" else "Save Check-In"
            Text(label)
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
    modifier = Modifier.fillMaxSize().background(
      Brush.verticalGradient(
        colors = listOf(
          MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f),
          MaterialTheme.colorScheme.background,
          MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.16f),
        )
      )
    ),
    contentPadding = PaddingValues(
      start = 20.dp, end = 20.dp,
      top = contentPadding.calculateTopPadding() + 20.dp,
      bottom = contentPadding.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      RecoveryHeroCard(
        eyebrow = "Insights",
        title = "AI-driven patterns for your recovery.",
        body = "Private, on-device analysis grounded in your check-in history.",
      )
    }

    // 1. Early Signals
    item {
      InsightSectionCard(
        title = "Early Signals",
        subtitle = "What has been shifting recently?",
        items = uiState.earlySignals,
        color = MaterialTheme.colorScheme.error
      )
    }

    // 2. Recurring Patterns (Gemma)
    // 3. Protective Factors (Gemma)
    when (val status = uiState.gemmaStatus) {
      is GemmaInsightStatus.Done -> {
        item {
          InsightSectionCard(
            title = "Recurring Patterns",
            subtitle = "What tends to happen together?",
            items = status.patterns.map { "${it.text}\n• ${it.evidence}" },
            color = Color(0xFFD55D3F)
          )
        }
        item {
          InsightSectionCard(
            title = "Protective Factors",
            subtitle = "What actually helps?",
            items = status.protective.map { "${it.text}\n• ${it.evidence}" },
            color = Color(0xFF317A52)
          )
        }
      }
      GemmaInsightStatus.Loading -> {
        item {
          Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator()
              Spacer(Modifier.height(8.dp))
              Text("Analyzing history...", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
      else -> {
        item {
          Button(
            onClick = { viewModel.generateInsights(modelManagerViewModel) },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Generate AI Insights")
          }
        }
      }
    }

    // 4. Consistency
    uiState.consistency?.let { c ->
      item {
        InsightSectionCard(
          title = "Consistency",
          subtitle = "Behavioral engagement with your recovery.",
          items = listOf(c.message),
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

@Composable
private fun RecoveryHistoryScreen(
  contentPadding: PaddingValues,
  viewModel: RecoveryViewModel,
  onEditEntry: (CheckInEntry) -> Unit
) {
  val history by viewModel.historyEntries.collectAsState()

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(
      Brush.verticalGradient(
        colors = listOf(
          MaterialTheme.colorScheme.surfaceContainerLow,
          MaterialTheme.colorScheme.background,
        )
      )
    ),
    contentPadding = PaddingValues(
      start = 20.dp, end = 20.dp,
      top = contentPadding.calculateTopPadding() + 20.dp,
      bottom = contentPadding.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      RecoveryHeroCard(
        eyebrow = "History",
        title = "Your recovery journey, day by day.",
        body = "View and edit your past entries to keep your record accurate.",
      )
      
      // Temporary Seed Button for Testing
      Button(
        onClick = { viewModel.seedMockData() },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
      ) {
        Text("Seed 21 Days of Mock Data")
      }
    }

    items(history) { entry ->
      HistoryCard(entry = entry, onEdit = { onEditEntry(entry) })
    }
  }
}

@Composable
private fun HistoryCard(entry: CheckInEntry, onEdit: () -> Unit) {
  val dateStr = remember(entry.date) {
    try {
      LocalDate.parse(entry.date).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    } catch (e: Exception) {
      entry.date
    }
  }

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(text = dateStr, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
          Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
      }

      Text(
        text = if (entry.cravingIntensity >= 7) "Difficult Day" else "Steady Day",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
      )

      if (entry.reflection.isNotEmpty()) {
        Text(
          text = entry.reflection,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entry.triggersList.forEach { tag ->
          TagItem(label = TriggerKeys.displayLabels[tag] ?: tag)
        }
      }
    }
  }
}

@Composable
private fun InsightSectionCard(title: String, subtitle: String, items: List<String>, color: Color) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      }
      Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      
      items.forEach { item ->
        Text(text = item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
      }
    }
  }
}

@Composable
private fun TriggerSlider(label: String, description: String, value: Float, onValueChange: (Float) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(text = value.toInt().toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = 1f..10f, steps = 8)
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
private fun RecoveryHeroCard(eyebrow: String, title: String, body: String) {
  Surface(
    color = Color.Transparent,
    shape = RoundedCornerShape(28.dp),
    shadowElevation = 2.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().background(
        brush = Brush.linearGradient(
          colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
          )
        )
      ).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(text = eyebrow.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
      Text(text = body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      content()
    }
  }
}
