package com.google.ai.edge.gallery.ui.recovery

data class InsightItem(
  val text: String,
  val evidence: String,
)

data class StoredInsightsSnapshot(
  val generatedAt: Long,
  val entryCount: Int,
  val earlySignals: List<InsightItem>,
  val recurringPatterns: List<InsightItem>,
  val protectiveFactors: List<InsightItem>,
  val consistency: List<InsightItem>,
  val retrievalSummary: String = "",
  val embeddingStatusSummary: String = "",
  val insightModelName: String = "",
  val insightAccelerator: String = "",
)
