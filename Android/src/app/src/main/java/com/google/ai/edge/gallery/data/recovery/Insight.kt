package com.google.ai.edge.gallery.data.recovery

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "insights")
data class Insight(
  @PrimaryKey val id: Long = System.currentTimeMillis(),
  val generatedAt: Long,
  val type: String,  // "daily" | "craving" | "weekly"
  val body: String,
)
