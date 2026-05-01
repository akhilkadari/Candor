package com.google.ai.edge.gallery.ui.recovery

import com.google.ai.edge.gallery.proto.CheckInEntry
import java.time.LocalDate

// Marlatt trigger taxonomy constants
object TriggerKeys {
  const val INTERPERSONAL_CONFLICT = "interpersonal_conflict"
  const val SOCIAL_PRESSURE = "social_pressure"
  const val SUBSTANCE_CUES = "substance_cues"
  const val FINANCIAL_STRESS = "financial_stress"
  const val WORK_STRESS = "work_stress"
  const val PHYSICAL_PAIN = "physical_pain"
  const val POSITIVE_CELEBRATION = "positive_celebration"
  const val NONE = "none"

  val displayLabels = mapOf(
    INTERPERSONAL_CONFLICT to "Interpersonal conflict",
    SOCIAL_PRESSURE to "Social pressure to use",
    SUBSTANCE_CUES to "Substance cues (places/people)",
    FINANCIAL_STRESS to "Financial stress",
    WORK_STRESS to "Work stress",
    PHYSICAL_PAIN to "Physical pain",
    POSITIVE_CELEBRATION to "Positive event / celebration",
    NONE to "None",
  )

  val all = displayLabels.keys.toList()
}

data class ConsistencyInsight(
  val currentStreak: Int,
  val missedThisWeek: Int,
  val totalEntries: Int,
  val message: String,
)

object InsightsEngine {

  const val ANALYSIS_SYSTEM_INSTRUCTION = """
You are a private, on-device recovery data analyst.
Analyze personal check-in data and report patterns as brief, specific, data-grounded observations.
Output only valid JSON. No disclaimers, no advice, no markdown fences, no text outside the JSON object.
"""

  fun computeEarlySignals(entries: List<CheckInEntry>): List<String> {
    if (entries.size < 4) return emptyList()

    val sorted = entries.sortedByDescending { it.date }
    val recent = sorted.take(3)
    val baseline = sorted.drop(3).take(11)
    if (baseline.isEmpty()) return emptyList()

    fun avg(list: List<CheckInEntry>, f: (CheckInEntry) -> Number) =
      list.map { f(it).toFloat() }.average().toFloat()

    data class Field(
      val name: String,
      val get: (CheckInEntry) -> Number,
      val higherIsBad: Boolean,
      val threshold: Float = 1.5f,
    )

    val fields = listOf(
      Field("Your craving intensity", { it.cravingIntensity }, true),
      Field("Stress", { it.stressLevel }, true),
      Field("Mood", { it.mood }, false),
      Field("Social connection", { it.socialConnection }, false),
      Field("Self-efficacy", { it.selfEfficacy }, false),
    )

    val signals = mutableListOf<String>()
    for (f in fields) {
      val recentAvg = avg(recent, f.get)
      val baselineAvg = avg(baseline, f.get)
      val delta = recentAvg - baselineAvg
      if (f.higherIsBad && delta > f.threshold) {
        signals.add("${f.name} has been higher over the past 3 days (avg ${fmt(recentAvg)} vs. your usual ${fmt(baselineAvg)}).")
      } else if (!f.higherIsBad && delta < -f.threshold) {
        signals.add("${f.name} has been lower than your baseline recently (avg ${fmt(recentAvg)} vs. ${fmt(baselineAvg)}).")
      }
    }
    return signals.ifEmpty { listOf("No significant changes compared to your recent baseline.") }
  }

  fun computeConsistency(entries: List<CheckInEntry>): ConsistencyInsight {
    if (entries.isEmpty()) {
      return ConsistencyInsight(0, 7, 0, "Start logging to track your consistency.")
    }

    val dates = entries.map { try { LocalDate.parse(it.date) } catch(e: Exception) { LocalDate.now() } }.toSortedSet(reverseOrder())
    val today = LocalDate.now()

    var streak = 0
    var cursor = today
    while (dates.contains(cursor)) {
      streak++
      cursor = cursor.minusDays(1)
    }

    val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
    val daysLoggedThisWeek = (0L..6L).count { dates.contains(monday.plusDays(it)) }.toInt()
    val missedThisWeek = 7 - daysLoggedThisWeek

    val message = when {
      streak >= 7 -> "$streak-day streak — keep it going."
      streak >= 3 -> "$streak-day streak. You're building momentum."
      streak >= 1 -> "You've checked in ${entries.size} of 7 days this week. Come back tomorrow."
      else -> "You've checked in $daysLoggedThisWeek of 7 days this week."
    }

    return ConsistencyInsight(streak, missedThisWeek, entries.size, message)
  }

  fun buildInsightPrompt(entries: List<CheckInEntry>): String {
    val recent = entries.sortedByDescending { it.date }.take(30)
    val header = "DATE|CRAV|MOOD|STRESS|SOCIAL|EFFICACY|TRIGGERS|REFLECTION"
    val rows = recent.joinToString("\n") { e ->
      "${e.date}|${e.cravingIntensity}|${e.mood}|" +
        "${e.stressLevel}|${e.socialConnection}|${e.selfEfficacy}|" +
        "${e.triggersList.joinToString(",")}|${e.reflection}"
    }
    return """
Field guide: craving_intensity (1-10, higher=worse), mood (1-10, higher=better),
stress_level (1-10, higher=worse), social_connection (1-10, higher=better),
self_efficacy (1-10, higher=better), triggers (comma-separated keys), reflection (text).

Entries (newest first):
$header
$rows

Return ONLY this JSON:
{
  "patterns": [
    {"text": "...", "evidence": "..."},
    {"text": "...", "evidence": "..."}
  ],
  "protective": [
    {"text": "...", "evidence": "..."},
    {"text": "...", "evidence": "..."}
  ]
}

Requirements:
- patterns: exactly 2 multi-signal observations; each must combine ≥2 different fields
- protective: exactly 2 positive correlations showing what correlates with better days
- text: 1-2 sentences in second person ("Your cravings tend to...")
- evidence: cite specific counts or timeframes ("on 4 of the past 7 days when...", "across your past week...")
- Use natural language only; never reference field codes like "craving_intensity"
    """.trimIndent()
  }

  private fun fmt(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(v)
}
