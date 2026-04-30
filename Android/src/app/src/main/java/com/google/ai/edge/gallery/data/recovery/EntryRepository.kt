package com.google.ai.edge.gallery.data.recovery

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(private val dao: EntryDao) {

  val allEntries: Flow<List<Entry>> = dao.getAllFlow()

  suspend fun save(entry: Entry) = dao.insert(entry)

  suspend fun getLastN(n: Int): List<Entry> = dao.getLastN(n)

  suspend fun getHighCravingDays(threshold: Int = 7): List<Entry> =
    dao.getHighCravingDays(threshold)

  suspend fun getSimilarDays(reference: Entry, limit: Int = 10): List<Entry> =
    dao.getSimilarDays(
      excludeId = reference.id,
      mood = reference.mood,
      stressLevel = reference.stressLevel,
      cravingIntensity = reference.cravingIntensity,
      limit = limit,
    )

  suspend fun delete(id: Long) = dao.deleteById(id)

  suspend fun deleteAll() = dao.deleteAll()
}
