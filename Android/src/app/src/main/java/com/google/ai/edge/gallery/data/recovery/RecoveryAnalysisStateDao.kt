package com.google.ai.edge.gallery.data.recovery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecoveryAnalysisStateDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(state: RecoveryAnalysisState)

  @Query("SELECT * FROM recovery_analysis_state WHERE id = 0 LIMIT 1")
  suspend fun get(): RecoveryAnalysisState?
}
