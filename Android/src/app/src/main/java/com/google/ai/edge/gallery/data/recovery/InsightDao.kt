package com.google.ai.edge.gallery.data.recovery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(insight: Insight)

  @Query("SELECT * FROM insights ORDER BY generatedAt DESC")
  fun getAllFlow(): Flow<List<Insight>>

  @Query("SELECT * FROM insights WHERE type = :type ORDER BY generatedAt DESC LIMIT 1")
  suspend fun getLatestByType(type: String): Insight?

  @Query("DELETE FROM insights")
  suspend fun deleteAll()
}
