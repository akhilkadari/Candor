package com.google.ai.edge.gallery.data.recovery

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
  @PrimaryKey val id: Long = System.currentTimeMillis(),
  val timestamp: Long,
  val mood: Int,
  val sleepHours: Float,
  val stressLevel: Int,
  val socialScore: Int,
  val cravingIntensity: Int,
  val freeText: String,
)
