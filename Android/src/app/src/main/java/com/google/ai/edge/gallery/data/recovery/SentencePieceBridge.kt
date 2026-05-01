package com.google.ai.edge.gallery.data.recovery

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TOKENIZER_ASSET = "recovery/sentencepiece.model"
private const val TOKENIZER_CACHE_FILE = "embeddinggemma_sentencepiece.model"

@Singleton
class SentencePieceBridge @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  companion object {
    init {
      System.loadLibrary("recovery_embedding_bridge")
    }
  }

  fun tokenize(text: String): IntArray {
    if (text.isBlank()) return intArrayOf()
    return nativeTokenize(ensureTokenizerFile().absolutePath, text)
  }

  private fun ensureTokenizerFile(): File {
    val output = File(context.filesDir, TOKENIZER_CACHE_FILE)
    if (output.exists() && output.length() > 0L) {
      return output
    }

    context.assets.open(TOKENIZER_ASSET).use { input ->
      output.outputStream().use { outputStream ->
        input.copyTo(outputStream)
      }
    }
    return output
  }

  private external fun nativeTokenize(modelPath: String, text: String): IntArray
}
