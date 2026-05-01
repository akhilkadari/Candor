package com.google.ai.edge.gallery.ui.recovery

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.google.ai.edge.gallery.ui.home.SettingsDialog
import com.google.ai.edge.gallery.ui.llmchat.LlmChatScreen
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
  recoveryViewModel: RecoveryViewModel = hiltViewModel()
) {
  var selectedTab by rememberSaveable { mutableStateOf(RecoveryTab.LOG) }
  var showAiChat by rememberSaveable { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  BackHandler(enabled = showAiChat) { showAiChat = false }

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
        snackbarHostState = snackbarHostState
      )
      RecoveryTab.INSIGHTS -> RecoveryInsightsScreen(contentPadding = innerPadding)
      RecoveryTab.HISTORY -> RecoveryHistoryScreen(contentPadding = innerPadding, viewModel = recoveryViewModel)
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
  snackbarHostState: SnackbarHostState
) {
  var cravings by rememberSaveable { mutableFloatStateOf(0f) }
  var mood by rememberSaveable { mutableFloatStateOf(0f) }
  var sleepQuality by rememberSaveable { mutableFloatStateOf(0f) }
  var stress by rememberSaveable { mutableFloatStateOf(0f) }
  var socialConnection by rememberSaveable { mutableFloatStateOf(0f) }
  var selfEfficacy by rememberSaveable { mutableFloatStateOf(0f) }
  var note by rememberSaveable { mutableStateOf("") }

  var immediateTriggerNote by rememberSaveable { mutableStateOf("") }
  var selectedTriggerOption by rememberSaveable { mutableStateOf<String?>(null) }
  
  val scope = rememberCoroutineScope()

  val triggerOptions = remember {
    listOf(
      "None",
      "Interpersonal Conflict",
      "Social Pressure to Use",
      "Exposure to Substance Cues (places/people)",
      "Financial Stress",
      "Work Stress",
      "Physical Pain",
      "Positive Event/Celebration",
      "Other"
    )
  }

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
      SectionCard(title = "How are you feeling today?", subtitle = "Use the sliders to log intensity today.") {
        TriggerSlider(
          label = "Craving intensity",
          description = "How strong were your urges to use today?",
          value = cravings,
          valueLabel = cravings.toInt().toString(),
          lowLabel = "None",
          highLabel = "Strong",
          onValueChange = { cravings = it },
        )
        TriggerSlider(
          label = "Mood",
          description = "Overall, how would you rate your mood today?",
          value = mood,
          valueLabel = mood.toInt().toString(),
          lowLabel = "Low",
          highLabel = "Steady",
          onValueChange = { mood = it },
        )
        TriggerSlider(
          label = "Sleep quality",
          description = "How well did you sleep last night?",
          value = sleepQuality,
          valueLabel = sleepQuality.toInt().toString(),
          lowLabel = "Restless",
          highLabel = "Rested",
          onValueChange = { sleepQuality = it },
        )
        TriggerSlider(
          label = "Stress level",
          description = "How stressed did you feel today?",
          value = stress,
          valueLabel = stress.toInt().toString(),
          lowLabel = "Calm",
          highLabel = "Overloaded",
          onValueChange = { stress = it },
        )
        TriggerSlider(
          label = "Social connection",
          description = "How connected to others did you feel today?",
          value = socialConnection,
          valueLabel = socialConnection.toInt().toString(),
          lowLabel = "Cut off",
          highLabel = "Connected",
          onValueChange = { socialConnection = it },
        )
        TriggerSlider(
          label = "Self-efficacy",
          description = "How confident are you in staying sober tomorrow?",
          value = selfEfficacy,
          valueLabel = if (selfEfficacy == 0f) "Not set" else selfEfficacy.toInt().toString(),
          lowLabel = "Unsure",
          highLabel = "Grounded",
          onValueChange = { selfEfficacy = it },
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
          triggerOptions.forEach { option ->
            val selected = selectedTriggerOption == option
            AssistChip(
              onClick = { selectedTriggerOption = option },
              label = { Text(option) },
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

        if (selectedTriggerOption == "Other") {
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = immediateTriggerNote,
            onValueChange = { immediateTriggerNote = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What happened?") },
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
          )
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
              "Persist your current state. This will save the slider values, trigger tags, and notes to the local database.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
          )
          Button(
            onClick = {
              val isAllSlidersSet = cravings > 0 && mood > 0 && sleepQuality > 0 && 
                                    stress > 0 && socialConnection > 0 && selfEfficacy > 0
              val isTriggerSet = selectedTriggerOption != null
              val isNoteSet = note.isNotBlank()
              
              if (!isAllSlidersSet || !isTriggerSet || !isNoteSet) {
                scope.launch {
                  snackbarHostState.showSnackbar("Please fill out all sliders, select a trigger, and add a reflection.")
                }
              } else {
                viewModel.saveEntry(
                  mood = mood.toInt(),
                  sleepQuality = sleepQuality.toInt(),
                  stressLevel = stress.toInt(),
                  socialConnection = socialConnection.toInt(),
                  cravingIntensity = cravings.toInt(),
                  selfEfficacy = selfEfficacy.toInt(),
                  trigger = if (selectedTriggerOption == "None") null else selectedTriggerOption,
                  triggerNote = if (selectedTriggerOption == "Other") immediateTriggerNote else null,
                  note = note
                )
                // Clear form after save
                mood = 0f
                sleepQuality = 0f
                stress = 0f
                socialConnection = 0f
                cravings = 0f
                selfEfficacy = 0f
                note = ""
                selectedTriggerOption = null
                immediateTriggerNote = ""
                
                scope.launch {
                  snackbarHostState.showSnackbar("Daily Check-In Saved")
                }
              }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            )
          ) {
            Text("Save Daily Check-In")
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
private fun RecoveryHistoryScreen(contentPadding: PaddingValues, viewModel: RecoveryViewModel) {
  val history by viewModel.allEntries.collectAsState()

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
@OptIn(ExperimentalLayoutApi::class)
private fun HistoryCard(entry: com.google.ai.edge.gallery.data.recovery.Entry) {
  val dateStr = remember(entry.timestamp) {
    val date = Date(entry.timestamp)
    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date)
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
        Text(text = dateStr, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (entry.trigger != null) {
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = entry.trigger,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }
      
      val headline = when {
        entry.cravingIntensity >= 7 -> "High Cravings"
        entry.stressLevel >= 7 -> "High Stress"
        entry.mood <= 3 -> "Low Mood"
        else -> "Steady Day"
      }
      
      Text(text = headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      
      if (entry.note.isNotEmpty()) {
        Text(
          text = entry.note,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TagItem(label = "Mood: ${entry.mood}")
        TagItem(label = "Stress: ${entry.stressLevel}")
        TagItem(label = "Cravings: ${entry.cravingIntensity}")
        TagItem(label = "Sleep: ${entry.sleepQuality}")
        if (entry.triggerNote != null) {
          TagItem(label = "Other: ${entry.triggerNote}")
        }
      }
    }
  }
}

@Composable
private fun TagItem(label: String) {
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(999.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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
