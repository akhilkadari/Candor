package com.google.ai.edge.gallery.data.recovery

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recovery_analysis_state")
data class RecoveryAnalysisState(
  @PrimaryKey val id: Int = 0,
  val insightsStale: Boolean = true,
  val staleReason: String = "No insights generated yet.",
  val lastRiskScore: Float = 0f,
  val lastProcessedEntryDate: String = "",
  val lastEmbeddingAccelerator: String = "",
  val lastEmbeddingModelName: String = "",
  val lastEmbeddingUpdatedAt: Long = 0L,
  val lastInsightsGeneratedAt: Long = 0L,
  val lastInsightModelName: String = "",
  val lastInsightAccelerator: String = "",
)
