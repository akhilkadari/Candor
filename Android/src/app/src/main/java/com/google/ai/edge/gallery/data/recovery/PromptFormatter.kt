package com.google.ai.edge.gallery.data.recovery

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PromptFormatter {

  private val dateFormat = SimpleDateFormat("MMM d", Locale.US)

  fun formatEntriesForPrompt(entries: List<Entry>): String {
    if (entries.isEmpty()) return "(no entries)"
    val header = "Date       | Mood | Sleep | Stress | Social | Craving | Notes"
    val divider = "-".repeat(70)
    val rows = entries.joinToString("\n") { e ->
      val date = dateFormat.format(Date(e.timestamp))
      "%-10s | %-4d | %-5.1f | %-6d | %-6d | %-7d | %s".format(
        date,
        e.mood,
        e.sleepHours,
        e.stressLevel,
        e.socialScore,
        e.cravingIntensity,
        e.freeText.take(60),
      )
    }
    return "$header\n$divider\n$rows"
  }
}
