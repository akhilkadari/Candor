package com.google.ai.edge.gallery.data.recovery

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Entry::class, Insight::class], version = 2, exportSchema = false)
abstract class RecoveryDatabase : RoomDatabase() {

  abstract fun entryDao(): EntryDao
  abstract fun insightDao(): InsightDao

  companion object {
    @Volatile private var INSTANCE: RecoveryDatabase? = null

    fun getInstance(context: Context): RecoveryDatabase =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          RecoveryDatabase::class.java,
          "recovery.db",
        ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
      }
  }
}
