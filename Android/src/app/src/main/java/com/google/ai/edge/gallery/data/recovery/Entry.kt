package com.google.ai.edge.gallery.data.recovery

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
  @PrimaryKey val id: Long = System.currentTimeMillis(),
  val timestamp: Long = System.currentTimeMillis(),
  val mood: Int,
  val sleepQuality: Int,
  val stressLevel: Int,
  val socialConnection: Int,
  val cravingIntensity: Int,
  val selfEfficacy: Int,
  val trigger: String? = null,
  val triggerNote: String? = null,
  val note: String,
)
