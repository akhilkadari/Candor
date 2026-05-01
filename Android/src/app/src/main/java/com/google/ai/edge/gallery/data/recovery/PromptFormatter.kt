package com.google.ai.edge.gallery.data.recovery

import com.google.ai.edge.gallery.proto.CheckInEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PromptFormatter {

  private val dateFormat = SimpleDateFormat("MMM d", Locale.US)

  fun formatEntriesForPrompt(entries: List<CheckInEntry>): String {
    if (entries.isEmpty()) return "(no entries)"
    val header = "Date       | Mood | Stress | Social | Craving | Efficacy | Reflection"
    val divider = "-".repeat(85)
    val rows = entries.joinToString("\n") { e ->
      val date = e.date
      "%-10s | %-4d | %-6d | %-6d | %-7d | %-8d | %s".format(
        date,
        e.mood,
        e.stressLevel,
        e.socialConnection,
        e.cravingIntensity,
        e.selfEfficacy,
        e.reflection.take(60),
      )
    }
    return "$header\n$divider\n$rows"
  }
}
