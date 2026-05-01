package com.google.ai.edge.gallery.data.recovery

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PromptFormatter {

  private val dateFormat = SimpleDateFormat("MMM d", Locale.US)

  fun formatEntriesForPrompt(entries: List<Entry>): String {
    if (entries.isEmpty()) return "(no entries)"
    val header = "Date       | Mood | Sleep | Stress | Social | Craving | Efficacy | Notes"
    val divider = "-".repeat(85)
    val rows = entries.joinToString("\n") { e ->
      val date = dateFormat.format(Date(e.timestamp))
      "%-10s | %-4d | %-5d | %-6d | %-6d | %-7d | %-8d | %s".format(
        date,
        e.mood,
        e.sleepQuality,
        e.stressLevel,
        e.socialConnection,
        e.cravingIntensity,
        e.selfEfficacy,
        e.note.take(60),
      )
    }
    return "$header\n$divider\n$rows"
  }
}
