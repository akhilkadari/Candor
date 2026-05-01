package com.google.ai.edge.gallery.data.recovery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EntryEmbeddingDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entryEmbedding: EntryEmbedding)

  @Query("SELECT * FROM entry_embeddings WHERE entryDate = :entryDate LIMIT 1")
  suspend fun getByEntryDate(entryDate: String): EntryEmbedding?

  @Query("SELECT * FROM entry_embeddings ORDER BY updatedAt DESC")
  suspend fun getAll(): List<EntryEmbedding>
}
