package com.google.ai.edge.gallery.data.recovery

interface RecoveryEmbeddingRuntime {
  suspend fun embed(text: String): EmbeddingRuntimeResult
}

data class EmbeddingRuntimeResult(
  val vector: List<Float>,
  val accelerator: String,
  val modelName: String,
  val errorMessage: String = "",
)

enum class EmbeddingGenerationStatus {
  READY,
  FAILED,
  SKIPPED,
}
