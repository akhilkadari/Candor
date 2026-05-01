package com.google.ai.edge.gallery.data.recovery

import com.google.ai.edge.gallery.proto.CheckInEntry
import com.google.ai.edge.gallery.ui.recovery.InsightItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

private const val RISK_STALENESS_THRESHOLD = 1.25f

data class RetrievedPatternContext(
  val riskyMatches: List<InsightItem>,
  val protectiveMatches: List<InsightItem>,
  val semanticMatches: List<InsightItem>,
)

@Singleton
class RecoveryAnalysisService @Inject constructor(
  private val embeddingRepository: EntryEmbeddingRepository,
  private val analysisStateRepository: RecoveryAnalysisStateRepository,
  private val embeddingRuntime: RecoveryEmbeddingRuntime,
) {

  suspend fun onCheckInSaved(entry: CheckInEntry) {
    val state = analysisStateRepository.get()
    val riskScore = calculateRiskScore(entry)
    val structuredSummary = buildStructuredSummary(entry, riskScore)
    val embeddingResult =
      if (entry.reflection.isBlank()) {
        EmbeddingRuntimeResult(
          vector = emptyList(),
          accelerator = "CPU",
          modelName = "EmbeddingGemma",
        )
      } else {
        embeddingRuntime.embed(entry.reflection)
      }

    val status =
      when {
        entry.reflection.isBlank() -> EmbeddingGenerationStatus.SKIPPED
        embeddingResult.errorMessage.isNotBlank() && embeddingResult.vector.isEmpty() ->
          EmbeddingGenerationStatus.FAILED
        else -> EmbeddingGenerationStatus.READY
      }

    embeddingRepository.upsert(
      EntryEmbedding(
        entryDate = entry.date,
        sourceText = entry.reflection,
        vectorJson = encodeVector(embeddingResult.vector),
        dimensions = embeddingResult.vector.size,
        norm = 1f,
        riskScore = riskScore,
        structuredSummary = structuredSummary,
        accelerator = embeddingResult.accelerator,
        status = status.name,
        updatedAt = System.currentTimeMillis(),
        modelName = embeddingResult.modelName,
        errorMessage = embeddingResult.errorMessage,
      )
    )

    val riskDelta = abs(riskScore - state.lastRiskScore)
    val staleReason =
      when {
        state.lastProcessedEntryDate.isBlank() ->
          "New check-in saved. Insights have not been generated yet."
        riskDelta >= RISK_STALENESS_THRESHOLD ->
          "Risk score changed materially after your latest check-in."
        else ->
          "New check-in added. Refresh insights to incorporate the latest pattern evidence."
      }
    analysisStateRepository.update(
      state.copy(
        insightsStale = true,
        staleReason = staleReason,
        lastRiskScore = riskScore,
        lastProcessedEntryDate = entry.date,
        lastEmbeddingAccelerator = embeddingResult.accelerator,
        lastEmbeddingModelName = embeddingResult.modelName,
        lastEmbeddingUpdatedAt = System.currentTimeMillis(),
      )
    )
  }

  suspend fun markInsightsFresh(modelName: String, accelerator: String) {
    val state = analysisStateRepository.get()
    analysisStateRepository.update(
      state.copy(
        insightsStale = false,
        staleReason = "",
        lastInsightsGeneratedAt = System.currentTimeMillis(),
        lastInsightModelName = modelName,
        lastInsightAccelerator = accelerator,
      )
    )
  }

  suspend fun getAnalysisState(): RecoveryAnalysisState = analysisStateRepository.get()

  suspend fun buildPatternContext(entries: List<CheckInEntry>): RetrievedPatternContext {
    val embeddings = embeddingRepository.getAll().associateBy { it.entryDate }
    val sorted = entries.sortedByDescending { it.date }
    val latest = sorted.firstOrNull()

    val semanticMatches =
      if (latest != null) {
        val latestEmbedding = embeddings[latest.date]
        if (latestEmbedding != null) {
          retrieveSemanticMatches(latestEmbedding, sorted.drop(1), embeddings)
        } else {
          emptyList()
        }
      } else {
        emptyList()
      }

    val riskyMatches =
      sorted
        .filter { calculateRiskScore(it) >= 6.5f }
        .take(3)
        .map { entry ->
          InsightItem(
            text = "${entry.date}: higher-risk pattern day",
            evidence = buildStructuredSummary(entry, calculateRiskScore(entry)),
          )
        }

    val protectiveMatches =
      sorted
        .filter { calculateRiskScore(it) <= 3.5f }
        .take(3)
        .map { entry ->
          InsightItem(
            text = "${entry.date}: more protective day",
            evidence = buildStructuredSummary(entry, calculateRiskScore(entry)),
          )
        }

    return RetrievedPatternContext(
      riskyMatches = riskyMatches,
      protectiveMatches = protectiveMatches,
      semanticMatches = semanticMatches,
    )
  }

  suspend fun getEmbeddingHealthSummary(): String {
    val all = embeddingRepository.getAll()
    if (all.isEmpty()) return "No embeddings generated yet."
    val readyCount = all.count { it.status == EmbeddingGenerationStatus.READY.name }
    val last = all.maxByOrNull { it.updatedAt }
    val fallbackCount = all.count { it.errorMessage.isNotBlank() }
    return buildString {
      append(
        "$readyCount/${all.size} entries have local embeddings. " +
          "Last accelerator: ${last?.accelerator ?: "CPU"}; " +
          "last dimensions: ${last?.dimensions ?: 0}."
      )
      if (fallbackCount > 0) {
        append(" ")
        append("$fallbackCount entries used an accelerator fallback or hit a model execution issue.")
      }
    }
  }

  fun calculateRiskScore(entry: CheckInEntry): Float {
    val stressRisk = entry.stressLevel * 0.35f
    val cravingRisk = entry.cravingIntensity * 0.35f
    val moodPenalty = (11 - entry.mood) * 0.10f
    val socialPenalty = (11 - entry.socialConnection) * 0.10f
    val efficacyPenalty = (11 - entry.selfEfficacy) * 0.10f
    return (stressRisk + cravingRisk + moodPenalty + socialPenalty + efficacyPenalty)
      .coerceIn(0f, 10f)
  }

  fun buildStructuredSummary(entry: CheckInEntry, riskScore: Float): String {
    val triggerText =
      if (entry.triggersList.isEmpty()) "no explicit triggers" else entry.triggersList.joinToString()
    return buildString {
      append("risk ")
      append("%.1f".format(riskScore))
      append("/10")
      append("; mood ")
      append(entry.mood)
      append(", stress ")
      append(entry.stressLevel)
      append(", craving ")
      append(entry.cravingIntensity)
      append(", social ")
      append(entry.socialConnection)
      append(", efficacy ")
      append(entry.selfEfficacy)
      append("; triggers: ")
      append(triggerText)
      if (entry.reflection.isNotBlank()) {
        append("; note: ")
        append(entry.reflection.take(140))
      }
    }
  }

  private fun retrieveSemanticMatches(
    latestEmbedding: EntryEmbedding,
    candidates: List<CheckInEntry>,
    embeddingsByDate: Map<String, EntryEmbedding>,
  ): List<InsightItem> {
    val latestVector = decodeVector(latestEmbedding.vectorJson)
    if (latestVector.isEmpty()) return emptyList()

    return candidates
      .mapNotNull { candidate ->
        val embedding = embeddingsByDate[candidate.date] ?: return@mapNotNull null
        val vector = decodeVector(embedding.vectorJson)
        if (vector.isEmpty()) return@mapNotNull null
        val score = cosineSimilarity(latestVector, vector)
        val evidence =
          "Similarity ${(score * 100).toInt()}%. ${buildStructuredSummary(candidate, embedding.riskScore)}"
        score to
          InsightItem(
            text = "${candidate.date}: semantically similar to your latest entry",
            evidence = evidence,
          )
      }
      .sortedByDescending { it.first }
      .take(3)
      .map { it.second }
  }

  private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
    val size = max(a.size, b.size)
    if (size == 0) return 0f
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (index in 0 until size) {
      val av = a.getOrElse(index) { 0f }
      val bv = b.getOrElse(index) { 0f }
      dot += av * bv
      normA += av * av
      normB += bv * bv
    }
    if (normA == 0f || normB == 0f) return 0f
    return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).coerceIn(-1f, 1f)
  }

  private fun encodeVector(vector: List<Float>): String = vector.joinToString(",")

  private fun decodeVector(raw: String): List<Float> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",").mapNotNull { it.toFloatOrNull() }
  }
}
