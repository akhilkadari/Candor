package com.google.ai.edge.gallery.ui.recovery

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.CheckInRepository
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.recovery.InsightRepository
import com.google.ai.edge.gallery.data.recovery.RecoveryAnalysisService
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "InsightsViewModel"
private const val MIN_ENTRIES = 5
private val PREFERRED_MODEL_NAMES = listOf(
  "Gemma-4-E2B-it",
  "Gemma-4-E4B-it",
  "Gemma-3n-E2B-it",
  "Gemma-3n-E4B-it",
  "Gemma3-1B-IT",
)

sealed class GemmaInsightStatus {
  object NoModel : GemmaInsightStatus()
  object NotEnoughData : GemmaInsightStatus()
  object Idle : GemmaInsightStatus()
  object Loading : GemmaInsightStatus()
  data class Done(val generatedAt: Long) : GemmaInsightStatus()
  data class Error(val message: String) : GemmaInsightStatus()
}

data class InsightsUiState(
  val snapshot: StoredInsightsSnapshot? = null,
  val gemmaStatus: GemmaInsightStatus = GemmaInsightStatus.Idle,
  val streamingText: String = "",
  val entryCount: Int = 0,
  val insightsStale: Boolean = true,
  val staleReason: String = "",
  val embeddingStatusSummary: String = "",
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
  private val checkInRepository: CheckInRepository,
  private val insightRepository: InsightRepository,
  private val recoveryAnalysisService: RecoveryAnalysisService,
  @param:ApplicationContext private val context: Context
) : ViewModel() {

  private val _uiState = MutableStateFlow(InsightsUiState())
  val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

  init {
    loadStoredInsights()
  }

  fun refresh() = loadStoredInsights()

  private fun loadStoredInsights() {
    viewModelScope.launch(Dispatchers.Default) {
      val snapshot = insightRepository.getLatestSnapshot()
      val analysisState = recoveryAnalysisService.getAnalysisState()
      val embeddingStatusSummary = recoveryAnalysisService.getEmbeddingHealthSummary()
      _uiState.update {
        it.copy(
          snapshot = snapshot,
          gemmaStatus =
            if (snapshot != null && it.gemmaStatus !is GemmaInsightStatus.Loading) {
              GemmaInsightStatus.Done(snapshot.generatedAt)
            } else {
              it.gemmaStatus
            },
          entryCount = checkInRepository.getEntryCount(),
          insightsStale = analysisState.insightsStale,
          staleReason = analysisState.staleReason,
          embeddingStatusSummary = embeddingStatusSummary,
        )
      }
    }
  }

  fun checkModelAvailability(modelManagerViewModel: ModelManagerViewModel) {
    val model = findAvailableModel(modelManagerViewModel)
    val entryCount = checkInRepository.getEntryCount()
    val newStatus = when {
      model == null -> GemmaInsightStatus.NoModel
      entryCount < MIN_ENTRIES -> GemmaInsightStatus.NotEnoughData
      _uiState.value.snapshot != null -> GemmaInsightStatus.Done(_uiState.value.snapshot!!.generatedAt)
      else -> GemmaInsightStatus.Idle
    }
    _uiState.update { it.copy(gemmaStatus = newStatus, entryCount = entryCount) }
  }

  private fun findAvailableModel(modelManagerViewModel: ModelManagerViewModel) =
    modelManagerViewModel.getAllDownloadedModels()
      .sortedBy { model ->
        val preferredIndex = PREFERRED_MODEL_NAMES.indexOf(model.name)
        if (preferredIndex >= 0) preferredIndex else Int.MAX_VALUE
      }
      .firstOrNull { model ->
        val acceleratorConfig =
          model.configs.firstOrNull { it.key == ConfigKeys.ACCELERATOR }
            as? com.google.ai.edge.gallery.data.SegmentedButtonConfig
        val options = acceleratorConfig?.options.orEmpty()
        options.contains(Accelerator.GPU.label) || options.contains(Accelerator.CPU.label)
      }

  fun generateInsights(modelManagerViewModel: ModelManagerViewModel) {
    if (_uiState.value.gemmaStatus is GemmaInsightStatus.Loading) return

    viewModelScope.launch(Dispatchers.Default) {
      val model = findAvailableModel(modelManagerViewModel)
      if (model == null) {
        _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.NoModel) }
        return@launch
      }

      _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.Loading, streamingText = "") }

      val task = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)
      if (task == null) {
        _uiState.update {
          it.copy(gemmaStatus = GemmaInsightStatus.Error("LLM chat task is unavailable."))
        }
        return@launch
      }

      val preferredAccelerator = getPreferredInsightAccelerator(model)

      val previousAccelerator =
        model.getStringConfigValue(
          key = ConfigKeys.ACCELERATOR,
          defaultValue = preferredAccelerator,
        )
      ensureModelAccelerator(model, preferredAccelerator)
      val initializationError =
        ensureModelInitialized(
          modelManagerViewModel = modelManagerViewModel,
          task = task,
          model = model,
          forceReinitialize = model.instance == null || previousAccelerator != preferredAccelerator,
        )

      val instance = model.instance as? LlmModelInstance
      if (instance == null) {
        _uiState.update {
          it.copy(
            gemmaStatus =
              GemmaInsightStatus.Error(
                initializationError
                  ?: "Model could not be initialized for ${model.name} using $preferredAccelerator."
              )
          )
        }
        return@launch
      }

      val entries = checkInRepository.getRecentEntries(30)
      if (entries.size < MIN_ENTRIES) {
        _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.NotEnoughData) }
        return@launch
      }

      val analysisState = recoveryAnalysisService.getAnalysisState()
      val patternContext = recoveryAnalysisService.buildPatternContext(entries)
      val prompt =
        InsightsEngine.buildInsightPrompt(
          entries = entries,
          patternContext = patternContext,
          staleReason = analysisState.staleReason.ifBlank { "Manual refresh requested." },
        )
      Log.d(TAG, "Prompt: $prompt")

      // Ensure any existing conversation is closed before creating a new one
      // The error "Only one session is supported at a time" suggests we should use the same or close first.
      // Since LlmModelInstance has a 'conversation' property, we'll use it or reset it.
      
      try {
        instance.conversation.close()
      } catch (e: Exception) {
        // Ignore if already closed or failed
      }

      val analysisConversation = instance.engine.createConversation(
        ConversationConfig(
          samplerConfig = null,
          systemInstruction = Contents.of(
            listOf(Content.Text(InsightsEngine.ANALYSIS_SYSTEM_INSTRUCTION.trimIndent()))
          ),
        )
      )
      instance.conversation = analysisConversation // Update the instance's active conversation

      var rawResponse = ""
      try {
        analysisConversation.sendMessageAsync(
          Contents.of(listOf(Content.Text(prompt))),
          object : MessageCallback {
            override fun onMessage(message: Message) {
              val text = message.toString()
              rawResponse += text
              _uiState.update { it.copy(streamingText = rawResponse) }
            }

            override fun onDone() {
              Log.d(TAG, "Gemma Response: $rawResponse")
              viewModelScope.launch(Dispatchers.Default) {
                val parsed = parseGemmaResponse(rawResponse)
                when (parsed) {
                  is ParsedGemmaError -> {
                    _uiState.update {
                      it.copy(
                        gemmaStatus = GemmaInsightStatus.Error(parsed.message),
                        streamingText = "",
                      )
                    }
                  }
                  is ParsedGemmaInsights -> {
                    val snapshot = StoredInsightsSnapshot(
                      generatedAt = System.currentTimeMillis(),
                      entryCount = entries.size,
                      earlySignals = InsightsEngine.computeEarlySignalItems(entries),
                      recurringPatterns = parsed.patterns,
                      protectiveFactors = parsed.protectiveFactors,
                      consistency = InsightsEngine.computeConsistencyItems(entries),
                      retrievalSummary =
                        "${patternContext.semanticMatches.size} semantic matches, ${patternContext.riskyMatches.size} risky evidence rows, ${patternContext.protectiveMatches.size} protective evidence rows.",
                      embeddingStatusSummary = recoveryAnalysisService.getEmbeddingHealthSummary(),
                      insightModelName = model.name,
                      insightAccelerator = preferredAccelerator,
                    )
                    insightRepository.saveSnapshot(snapshot)
                    recoveryAnalysisService.markInsightsFresh(
                      modelName = model.name,
                      accelerator = preferredAccelerator,
                    )
                    _uiState.update {
                      it.copy(
                        snapshot = snapshot,
                        gemmaStatus = GemmaInsightStatus.Done(snapshot.generatedAt),
                        streamingText = "",
                        entryCount = entries.size,
                        insightsStale = false,
                        staleReason = "",
                        embeddingStatusSummary = snapshot.embeddingStatusSummary,
                      )
                    }
                  }
                }
                analysisConversation.close()
              }
            }

            override fun onError(throwable: Throwable) {
              Log.e(TAG, "Inference error", throwable)
              _uiState.update {
                it.copy(
                  gemmaStatus = GemmaInsightStatus.Error("Analysis failed: ${throwable.message}"),
                  streamingText = "",
                )
              }
              analysisConversation.close()
            }
          },
          emptyMap(),
        )
      } catch (e: Exception) {
        Log.e(TAG, "Exception launching inference", e)
        _uiState.update {
          it.copy(
            gemmaStatus = GemmaInsightStatus.Error("Analysis failed: ${e.message}"),
            streamingText = "",
          )
        }
        analysisConversation.close()
      }
    }
  }

  private fun getPreferredInsightAccelerator(model: com.google.ai.edge.gallery.data.Model): String {
    val acceleratorConfig =
      model.configs.firstOrNull { it.key == ConfigKeys.ACCELERATOR }
        as? com.google.ai.edge.gallery.data.SegmentedButtonConfig
    val options = acceleratorConfig?.options.orEmpty()
    return when {
      options.contains(Accelerator.GPU.label) -> Accelerator.GPU.label
      else -> Accelerator.CPU.label
    }
  }

  private fun ensureModelAccelerator(
    model: com.google.ai.edge.gallery.data.Model,
    accelerator: String,
  ) {
    val newConfigValues = model.configValues.toMutableMap()
    newConfigValues[ConfigKeys.ACCELERATOR.label] = accelerator
    model.configValues = newConfigValues
  }

  private suspend fun ensureModelInitialized(
    modelManagerViewModel: ModelManagerViewModel,
    task: com.google.ai.edge.gallery.data.Task,
    model: com.google.ai.edge.gallery.data.Model,
    forceReinitialize: Boolean,
  ): String? {
    Log.d(
      TAG,
      "Initializing insights model with file ${model.downloadFileName} on ${model.getStringConfigValue(ConfigKeys.ACCELERATOR)} force=$forceReinitialize"
    )

    if (model.instance != null && !forceReinitialize) {
      return null
    }

    modelManagerViewModel.initializeModel(context, task, model, force = forceReinitialize)

    var retries = 100
    while (retries > 0) {
      if (model.instance != null) {
        return null
      }

      val initStatus =
        modelManagerViewModel.uiState.value.modelInitializationStatus[model.name]

      if (initStatus?.status == ModelInitializationStatusType.ERROR) {
        return initStatus.error.ifBlank {
          "Failed to initialize ${model.name} on ${model.getStringConfigValue(ConfigKeys.ACCELERATOR)}."
        }
      }

      kotlinx.coroutines.delay(200)
      retries--
    }

    return "Timed out initializing ${model.name} on ${model.getStringConfigValue(ConfigKeys.ACCELERATOR)}."
  }

  private sealed interface ParsedGemmaResponse

  private data class ParsedGemmaInsights(
    val patterns: List<InsightItem>,
    val protectiveFactors: List<InsightItem>,
  ) : ParsedGemmaResponse

  private data class ParsedGemmaError(
    val message: String,
  ) : ParsedGemmaResponse

  private data class RawInsightItem(
    val text: String = "",
    val evidence: String = "",
  )

  private data class RawResponse(
    val patterns: List<RawInsightItem> = emptyList(),
    val protective: List<RawInsightItem> = emptyList(),
    val protectiveFactors: List<RawInsightItem> = emptyList(),
  )

  private fun parseGemmaResponse(raw: String): ParsedGemmaResponse {
    return try {
      val start = raw.indexOf('{')
      val end = raw.lastIndexOf('}')
      if (start == -1 || end == -1 || start >= end) {
        Log.e(TAG, "JSON block not found in response: $raw")
        return ParsedGemmaError("Invalid model output format.")
      }
      val json = raw.substring(start, end + 1)

      val parsed = Gson().fromJson(json, RawResponse::class.java)
      val patterns = parsed.patterns.mapNotNull(::toInsightItem).take(2)
      val protectiveFactors = parsed.protectiveFactors.ifEmpty { parsed.protective }
        .mapNotNull(::toInsightItem)
        .take(2)

      if (patterns.isEmpty() || protectiveFactors.isEmpty()) {
        return ParsedGemmaError("Model output was missing required sections.")
      }

      ParsedGemmaInsights(
        patterns = patterns,
        protectiveFactors = protectiveFactors,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Parse failed. Raw: $raw", e)
      ParsedGemmaError("Could not parse model output. Try again.")
    }
  }

  private fun toInsightItem(item: RawInsightItem): InsightItem? {
    if (item.text.isBlank() || item.evidence.isBlank()) return null
    return InsightItem(text = item.text.trim(), evidence = item.evidence.trim())
  }
}
