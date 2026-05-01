package com.google.ai.edge.gallery.data.recovery

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryEmbeddingRepository @Inject constructor(
  private val dao: EntryEmbeddingDao,
) {
  suspend fun upsert(entryEmbedding: EntryEmbedding) = dao.insert(entryEmbedding)

  suspend fun getByEntryDate(entryDate: String): EntryEmbedding? = dao.getByEntryDate(entryDate)

  suspend fun getAll(): List<EntryEmbedding> = dao.getAll()
}
