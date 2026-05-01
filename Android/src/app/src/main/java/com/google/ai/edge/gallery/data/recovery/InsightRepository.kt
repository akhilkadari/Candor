package com.google.ai.edge.gallery.data.recovery

import com.google.ai.edge.gallery.ui.recovery.StoredInsightsSnapshot
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

private const val SNAPSHOT_TYPE = "recovery_insights_snapshot"

@Singleton
class InsightRepository @Inject constructor(
  private val dao: InsightDao,
) {
  private val gson = Gson()

  suspend fun getLatestSnapshot(): StoredInsightsSnapshot? {
    val body = dao.getLatestByType(SNAPSHOT_TYPE)?.body ?: return null
    return runCatching { gson.fromJson(body, StoredInsightsSnapshot::class.java) }.getOrNull()
  }

  suspend fun saveSnapshot(snapshot: StoredInsightsSnapshot) {
    dao.insert(
      Insight(
        generatedAt = snapshot.generatedAt,
        type = SNAPSHOT_TYPE,
        body = gson.toJson(snapshot),
      )
    )
  }
}
