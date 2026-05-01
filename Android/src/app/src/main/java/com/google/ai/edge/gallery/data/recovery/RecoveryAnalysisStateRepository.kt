package com.google.ai.edge.gallery.data.recovery

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecoveryAnalysisStateRepository @Inject constructor(
  private val dao: RecoveryAnalysisStateDao,
) {
  suspend fun get(): RecoveryAnalysisState = dao.get() ?: RecoveryAnalysisState()

  suspend fun update(state: RecoveryAnalysisState) = dao.upsert(state)
}
