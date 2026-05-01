package com.google.ai.edge.gallery.ui.recovery

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.CheckInRepository
import com.google.ai.edge.gallery.proto.CheckInEntry
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
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
private const val MIN_ENTRIES = 4
private const val PREFERRED_MODEL_NAME = "Gemma-3n-E2B-it"

data class InsightItem(val text: String, val evidence: String)

sealed class GemmaInsightStatus {
  object NoModel : GemmaInsightStatus()
  object NotEnoughData : GemmaInsightStatus()
  object Idle : GemmaInsightStatus()
  object Loading : GemmaInsightStatus()
  data class Done(
    val patterns: List<InsightItem>,
    val protective: List<InsightItem>,
    val entryCount: Int,
    val evidenceEntries: List<CheckInEntry> = emptyList(),
  ) : GemmaInsightStatus()
  data class Error(val message: String) : GemmaInsightStatus()
}

data class InsightsUiState(
  val earlySignals: List<String> = emptyList(),
  val consistency: ConsistencyInsight? = null,
  val gemmaStatus: GemmaInsightStatus = GemmaInsightStatus.Idle,
  val streamingText: String = "",
  val entryCount: Int = 0,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
  private val checkInRepository: CheckInRepository,
  @param:ApplicationContext private val context: Context
) : ViewModel() {

  private val _uiState = MutableStateFlow(InsightsUiState())
  val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

  init {
    loadRuleBasedInsights()
  }

  fun refresh() = loadRuleBasedInsights()

  private fun loadRuleBasedInsights() {
    viewModelScope.launch(Dispatchers.Default) {
      val entries = checkInRepository.getAllEntries()
      _uiState.update {
        it.copy(
          earlySignals = InsightsEngine.computeEarlySignals(entries),
          consistency = InsightsEngine.computeConsistency(entries),
          entryCount = entries.size,
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
      _uiState.value.gemmaStatus is GemmaInsightStatus.Done -> _uiState.value.gemmaStatus
      else -> GemmaInsightStatus.Idle
    }
    _uiState.update { it.copy(gemmaStatus = newStatus, entryCount = entryCount) }
  }

  private fun findAvailableModel(modelManagerViewModel: ModelManagerViewModel) =
    modelManagerViewModel.getAllDownloadedModels().firstOrNull { it.name == PREFERRED_MODEL_NAME }
      ?: modelManagerViewModel.getAllDownloadedModels().firstOrNull()

  fun generateInsights(modelManagerViewModel: ModelManagerViewModel) {
    if (_uiState.value.gemmaStatus is GemmaInsightStatus.Loading) return

    viewModelScope.launch(Dispatchers.Default) {
      val model = findAvailableModel(modelManagerViewModel)
      if (model == null) {
        _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.NoModel) }
        return@launch
      }

      _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.Loading, streamingText = "") }

      // Initialize if needed
      if (model.instance == null) {
        Log.d(TAG, "Model instance null, initializing...")
        val task = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)
        if (task != null) {
          modelManagerViewModel.initializeModel(context, task, model, force = false)
          // Wait for initialization
          var retries = 50
          while (model.instance == null && retries > 0) {
            kotlinx.coroutines.delay(200)
            retries--
          }
        }
      }

      val instance = model.instance as? LlmModelInstance
      if (instance == null) {
        _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.Error("Model could not be initialized.")) }
        return@launch
      }

      val entries = checkInRepository.getRecentEntries(30)
      if (entries.size < MIN_ENTRIES) {
        _uiState.update { it.copy(gemmaStatus = GemmaInsightStatus.NotEnoughData) }
        return@launch
      }

      val prompt = InsightsEngine.buildInsightPrompt(entries)
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
          samplerConfig = SamplerConfig(topK = 1, temperature = 0.1, topP = 1.0),
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
              val parsed = parseGemmaResponse(rawResponse, entries.size, entries.take(3))
              _uiState.update { it.copy(gemmaStatus = parsed, streamingText = "") }
              analysisConversation.close()
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

  private fun parseGemmaResponse(
    raw: String,
    entryCount: Int,
    evidenceEntries: List<CheckInEntry>
  ): GemmaInsightStatus {
    return try {
      val start = raw.indexOf('{')
      val end = raw.lastIndexOf('}')
      if (start == -1 || end == -1) {
          Log.e(TAG, "JSON block not found in response: $raw")
          return GemmaInsightStatus.Error("Invalid model output format.")
      }
      val json = raw.substring(start, end + 1)

      data class RawInsightItem(val text: String = "", val evidence: String = "")
      data class RawResponse(
        val patterns: List<RawInsightItem> = emptyList(),
        val protective: List<RawInsightItem> = emptyList(),
      )

      val parsed = Gson().fromJson(json, RawResponse::class.java)
      GemmaInsightStatus.Done(
        patterns = parsed.patterns.map { InsightItem(it.text, it.evidence) },
        protective = parsed.protective.map { InsightItem(it.text, it.evidence) },
        entryCount = entryCount,
        evidenceEntries = evidenceEntries,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Parse failed. Raw: $raw", e)
      GemmaInsightStatus.Error("Could not parse model output. Try again.")
    }
  }
}
