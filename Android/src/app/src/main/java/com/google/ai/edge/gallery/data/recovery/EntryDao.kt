package com.google.ai.edge.gallery.data.recovery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: Entry)

  @Query("SELECT * FROM entries ORDER BY timestamp DESC")
  fun getAllFlow(): Flow<List<Entry>>

  @Query("SELECT * FROM entries ORDER BY timestamp DESC")
  suspend fun getAll(): List<Entry>

  @Query("SELECT * FROM entries ORDER BY timestamp DESC LIMIT :n")
  suspend fun getLastN(n: Int): List<Entry>

  @Query("SELECT * FROM entries WHERE cravingIntensity >= :threshold ORDER BY timestamp DESC")
  suspend fun getHighCravingDays(threshold: Int): List<Entry>

  /**
   * Returns entries where mood, stress, and craving are all within ±2 of the reference values,
   * excluding the entry with the given id so a day isn't compared to itself.
   */
  @Query(
    """
    SELECT * FROM entries
    WHERE id != :excludeId
      AND ABS(mood - :mood) <= 2
      AND ABS(stressLevel - :stressLevel) <= 2
      AND ABS(cravingIntensity - :cravingIntensity) <= 2
    ORDER BY timestamp DESC
    LIMIT :limit
    """
  )
  suspend fun getSimilarDays(
    excludeId: Long,
    mood: Int,
    stressLevel: Int,
    cravingIntensity: Int,
    limit: Int = 10,
  ): List<Entry>

  @Query("DELETE FROM entries WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("SELECT * FROM entries WHERE timestamp >= :from ORDER BY timestamp DESC")
  suspend fun getEntriesSince(from: Long): List<Entry>

  @Query("DELETE FROM entries")
  suspend fun deleteAll()
}
