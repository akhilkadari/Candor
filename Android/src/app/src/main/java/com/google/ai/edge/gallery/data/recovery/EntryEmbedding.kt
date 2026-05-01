package com.google.ai.edge.gallery.data.recovery

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entry_embeddings")
data class EntryEmbedding(
  @PrimaryKey val entryDate: String,
  val sourceText: String,
  val vectorJson: String,
  val dimensions: Int,
  val norm: Float,
  val riskScore: Float,
  val structuredSummary: String,
  val accelerator: String,
  val status: String,
  val updatedAt: Long,
  val modelName: String,
  val errorMessage: String = "",
)
