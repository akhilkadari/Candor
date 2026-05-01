package com.google.ai.edge.gallery.data.recovery

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.litert.Accelerator as LiteRtAccelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "RecoveryEmbeddingRt"
private const val EMBEDDING_MODEL_ASSET = "recovery/embedder.tflite"
private const val TOKENIZER_ASSET = "recovery/sentencepiece.model"
private const val EMBEDDING_MODEL_NAME = "EmbeddingGemma"
private const val EMBEDDING_TASK_PREFIX = "task: sentence similarity | query: "
private const val QUALCOMM_DISPATCH_LIBRARY = "libLiteRtDispatch_Qualcomm.so"
private const val EMBEDDING_SEQUENCE_LENGTH = 512
private val VALID_EMBEDDING_DIMENSIONS = setOf(128, 256, 512, 768)

@Singleton
class LiteRtCompiledEmbeddingRuntime @Inject constructor(
  @ApplicationContext private val context: Context,
  private val sentencePieceBridge: SentencePieceBridge,
) : RecoveryEmbeddingRuntime {
  private val sessionLock = Any()

  @Volatile private var npuSession: EmbeddingSession? = null
  @Volatile private var cpuSession: EmbeddingSession? = null

  override suspend fun embed(text: String): EmbeddingRuntimeResult = withContext(Dispatchers.Default) {
    val normalized = text.trim()
    if (normalized.isEmpty()) {
      return@withContext EmbeddingRuntimeResult(
        vector = emptyList(),
        accelerator = "CPU",
        modelName = EMBEDDING_MODEL_NAME,
      )
    }

    if (!assetExists(EMBEDDING_MODEL_ASSET) || !assetExists(TOKENIZER_ASSET)) {
      return@withContext EmbeddingRuntimeResult(
        vector = emptyList(),
        accelerator = "CPU",
        modelName = EMBEDDING_MODEL_NAME,
        errorMessage =
          "Missing EmbeddingGemma assets. Expected both $EMBEDDING_MODEL_ASSET and $TOKENIZER_ASSET.",
      )
    }

    val promptedText = EMBEDDING_TASK_PREFIX + normalized
    val tokenIds =
      runCatching { sentencePieceBridge.tokenize(promptedText) }
        .getOrElse {
          return@withContext EmbeddingRuntimeResult(
            vector = emptyList(),
            accelerator = "CPU",
            modelName = EMBEDDING_MODEL_NAME,
            errorMessage = "SentencePiece tokenization failed: ${it.message}",
          )
        }
    if (tokenIds.isEmpty()) {
      return@withContext EmbeddingRuntimeResult(
        vector = emptyList(),
        accelerator = "CPU",
        modelName = EMBEDDING_MODEL_NAME,
        errorMessage = "SentencePiece tokenization returned no tokens.",
      )
    }

    val preparedInputs = buildModelInputs(tokenIds)
    val preferNpu = shouldPreferNpu()
    val primaryAccelerator = if (preferNpu) LiteRtAccelerator.NPU else LiteRtAccelerator.CPU
    val primaryAttempt = runCompiledInference(preparedInputs, primaryAccelerator)
    if (primaryAttempt.isSuccess) {
      return@withContext EmbeddingRuntimeResult(
        vector = primaryAttempt.getOrThrow(),
        accelerator = if (preferNpu) "NPU" else "CPU",
        modelName = EMBEDDING_MODEL_NAME,
      )
    }

    val primaryError = primaryAttempt.exceptionOrNull()
    if (preferNpu) {
      val cpuAttempt = runCompiledInference(preparedInputs, LiteRtAccelerator.CPU)
      if (cpuAttempt.isSuccess) {
        return@withContext EmbeddingRuntimeResult(
          vector = cpuAttempt.getOrThrow(),
          accelerator = "CPU",
          modelName = EMBEDDING_MODEL_NAME,
          errorMessage =
            "NPU embedding path failed on SM8750; CPU fallback used: ${primaryError?.message ?: "unknown error"}",
        )
      }

      val cpuError = cpuAttempt.exceptionOrNull()
      return@withContext EmbeddingRuntimeResult(
        vector = emptyList(),
        accelerator = "CPU",
        modelName = EMBEDDING_MODEL_NAME,
        errorMessage =
          "EmbeddingGemma inference failed on NPU and CPU fallback. NPU: ${primaryError?.message ?: "unknown error"}; CPU: ${cpuError?.message ?: "unknown error"}",
      )
    }

    EmbeddingRuntimeResult(
      vector = emptyList(),
      accelerator = "CPU",
      modelName = EMBEDDING_MODEL_NAME,
      errorMessage = "EmbeddingGemma CPU inference failed: ${primaryError?.message ?: "unknown error"}",
    )
  }

  private fun runCompiledInference(
    preparedInputs: PreparedEmbeddingInputs,
    accelerator: LiteRtAccelerator,
  ): Result<List<Float>> {
    return runCatching {
      val session = getOrCreateSession(accelerator)
      val inputBuffers = session.model.createInputBuffers()
      val outputBuffers = session.model.createOutputBuffers()
      writeInputs(inputBuffers, preparedInputs)
      session.model.run(inputBuffers, outputBuffers)
      val output = outputBuffers.firstOrNull()?.readFloat() ?: error("EmbeddingGemma returned no outputs.")
      if (output.isEmpty()) {
        error("EmbeddingGemma returned an empty embedding vector.")
      }
      if (output.size !in VALID_EMBEDDING_DIMENSIONS) {
        error("Unexpected EmbeddingGemma output size ${output.size}. Expected one of $VALID_EMBEDDING_DIMENSIONS.")
      }
      output.toList()
    }.onFailure { error ->
      Log.e(TAG, "Compiled embedding inference failed on ${accelerator.name}", error)
      invalidateSession(accelerator)
    }
  }

  private fun writeInputs(
    inputBuffers: List<TensorBuffer>,
    preparedInputs: PreparedEmbeddingInputs,
  ) {
    require(inputBuffers.isNotEmpty()) { "EmbeddingGemma model has no input buffers." }
    inputBuffers[0].writeInt(preparedInputs.tokenIds)
    if (inputBuffers.size >= 2) {
      inputBuffers[1].writeInt(preparedInputs.attentionMask)
    }
    if (inputBuffers.size >= 3) {
      inputBuffers[2].writeInt(preparedInputs.segmentIds)
    }
    if (inputBuffers.size > 3) {
      for (index in 3 until inputBuffers.size) {
        inputBuffers[index].writeInt(preparedInputs.segmentIds)
      }
    }
  }

  private fun buildModelInputs(tokenIds: IntArray): PreparedEmbeddingInputs {
    val trimmed = if (tokenIds.size > EMBEDDING_SEQUENCE_LENGTH) tokenIds.copyOfRange(0, EMBEDDING_SEQUENCE_LENGTH) else tokenIds
    val ids = IntArray(EMBEDDING_SEQUENCE_LENGTH)
    val mask = IntArray(EMBEDDING_SEQUENCE_LENGTH)
    val segments = IntArray(EMBEDDING_SEQUENCE_LENGTH)
    trimmed.copyInto(ids, endIndex = trimmed.size)
    for (index in trimmed.indices) {
      mask[index] = 1
    }
    return PreparedEmbeddingInputs(ids, mask, segments)
  }

  private fun getOrCreateSession(accelerator: LiteRtAccelerator): EmbeddingSession {
    cachedSession(accelerator)?.let { return it }
    synchronized(sessionLock) {
      cachedSession(accelerator)?.let { return it }
      val created =
        when (accelerator) {
          LiteRtAccelerator.NPU -> {
            val provider = BuiltinNpuAcceleratorProvider(context)
            val environment = Environment.create(provider)
            EmbeddingSession(
              model =
                CompiledModel.create(
                  context.assets,
                  EMBEDDING_MODEL_ASSET,
                  CompiledModel.Options(LiteRtAccelerator.NPU),
                  environment,
                ),
              environment = environment,
            )
          }
          LiteRtAccelerator.CPU -> {
            EmbeddingSession(
              model =
                CompiledModel.create(
                  context.assets,
                  EMBEDDING_MODEL_ASSET,
                  CompiledModel.Options(LiteRtAccelerator.CPU),
                )
            )
          }
          else -> error("Unsupported embedding accelerator ${accelerator.name}")
        }
      cacheSession(accelerator, created)
      return created
    }
  }

  private fun invalidateSession(accelerator: LiteRtAccelerator) {
    synchronized(sessionLock) {
      val session =
        when (accelerator) {
          LiteRtAccelerator.NPU -> npuSession.also { npuSession = null }
          LiteRtAccelerator.CPU -> cpuSession.also { cpuSession = null }
          else -> null
        } ?: return
      runCatching { session.model.close() }
      runCatching { session.environment?.close() }
    }
  }

  private fun cachedSession(accelerator: LiteRtAccelerator): EmbeddingSession? {
    return when (accelerator) {
      LiteRtAccelerator.NPU -> npuSession
      LiteRtAccelerator.CPU -> cpuSession
      else -> null
    }
  }

  private fun cacheSession(accelerator: LiteRtAccelerator, session: EmbeddingSession) {
    when (accelerator) {
      LiteRtAccelerator.NPU -> npuSession = session
      LiteRtAccelerator.CPU -> cpuSession = session
      else -> Unit
    }
  }

  private fun assetExists(assetPath: String): Boolean {
    return try {
      context.assets.open(assetPath).close()
      true
    } catch (_: Exception) {
      false
    }
  }

  private fun shouldPreferNpu(): Boolean {
    val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else ""
    val isSm8750 = socModel.equals("sm8750", ignoreCase = true)
    val nativeLibDir = context.applicationInfo.nativeLibraryDir ?: return false
    return isSm8750 && java.io.File(nativeLibDir, QUALCOMM_DISPATCH_LIBRARY).exists()
  }
}

private data class PreparedEmbeddingInputs(
  val tokenIds: IntArray,
  val attentionMask: IntArray,
  val segmentIds: IntArray,
)

private data class EmbeddingSession(
  val model: CompiledModel,
  val environment: Environment? = null,
)
