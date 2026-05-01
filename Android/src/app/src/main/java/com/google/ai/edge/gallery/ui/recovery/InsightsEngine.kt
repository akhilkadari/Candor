package com.google.ai.edge.gallery.ui.recovery

import com.google.ai.edge.gallery.proto.CheckInEntry
import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs

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

  fun computeEarlySignalItems(entries: List<CheckInEntry>): List<InsightItem> {
    if (entries.size < 4) return emptyList()

    val sorted = entries.sortedByDescending { it.date }
    val recent = sorted.take(3)
    val baseline = sorted.drop(3).take(7)
    if (baseline.isEmpty()) return emptyList()

    fun avg(list: List<CheckInEntry>, f: (CheckInEntry) -> Number) =
      list.map { f(it).toFloat() }.average().toFloat()

    data class Field(
      val label: String,
      val get: (CheckInEntry) -> Number,
      val higherIsBad: Boolean,
      val threshold: Float = 1.0f,
      val risingText: (Float, Float) -> String,
      val fallingText: (Float, Float) -> String,
    )

    val fields = listOf(
      Field(
        label = "Stress",
        get = { it.stressLevel },
        higherIsBad = true,
        risingText = { _, _ ->
          "Stress has been climbing over the past few check-ins."
        },
        fallingText = { _, _ -> "" },
      ),
      Field(
        label = "Craving intensity",
        get = { it.cravingIntensity },
        higherIsBad = true,
        risingText = { _, _ ->
          "Craving intensity has been running higher than your recent baseline."
        },
        fallingText = { _, _ -> "" },
      ),
      Field(
        label = "Mood",
        get = { it.mood },
        higherIsBad = false,
        risingText = { _, _ -> "" },
        fallingText = { _, _ ->
          "Mood has been lower across the last few entries."
        },
      ),
      Field(
        label = "Social connection",
        get = { it.socialConnection },
        higherIsBad = false,
        risingText = { _, _ -> "" },
        fallingText = { _, _ ->
          "Social connection has dipped recently."
        },
      ),
      Field(
        label = "Self-efficacy",
        get = { it.selfEfficacy },
        higherIsBad = false,
        risingText = { _, _ -> "" },
        fallingText = { _, _ ->
          "Confidence in staying sober tomorrow has been softer than usual."
        },
      ),
    )

    val signals = mutableListOf<Pair<Float, InsightItem>>()
    for (field in fields) {
      val recentAvg = avg(recent, field.get)
      val baselineAvg = avg(baseline, field.get)
      val delta = recentAvg - baselineAvg
      val evidence =
        "Past 3 check-ins avg ${fmt(recentAvg)} vs previous ${baseline.size} avg ${fmt(baselineAvg)}."

      if (field.higherIsBad && delta >= field.threshold) {
        signals += delta to InsightItem(
          text = field.risingText(recentAvg, baselineAvg),
          evidence = evidence,
        )
      } else if (!field.higherIsBad && delta <= -field.threshold) {
        signals += abs(delta) to InsightItem(
          text = field.fallingText(recentAvg, baselineAvg),
          evidence = evidence,
        )
      }
    }

    val recentDates = recent.mapNotNull(::parseDate)
    if (recentDates.size >= 2) {
      val maxGap = recentDates.zipWithNext { newer, older ->
        abs(Duration.between(older.atStartOfDay(), newer.atStartOfDay()).toDays().toInt()) + 1
      }
        .maxOrNull() ?: 1
      if (maxGap >= 3) {
        signals += 1.0f to InsightItem(
          text = "Your recent check-ins have been spaced out more unevenly.",
          evidence = "There was a ${maxGap - 1}-day gap between two of your last 3 entries.",
        )
      }
    }

    return signals.sortedByDescending { it.first }.map { it.second }.take(3)
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
      streak >= 1 -> "You've checked in $daysLoggedThisWeek of 7 days this week. Come back tomorrow."
      else -> "You've checked in $daysLoggedThisWeek of 7 days this week."
    }

    return ConsistencyInsight(streak, missedThisWeek, entries.size, message)
  }

  fun computeConsistencyItems(entries: List<CheckInEntry>): List<InsightItem> {
    if (entries.isEmpty()) {
      return listOf(
        InsightItem(
          text = "You have not started a check-in rhythm yet.",
          evidence = "No saved entries yet.",
        )
      )
    }

    val sorted = entries.sortedBy { it.date }
    val parsedDates = sorted.mapNotNull(::parseDate)
    val consistency = computeConsistency(entries)
    val items = mutableListOf<InsightItem>()

    val last7Cutoff = LocalDate.now().minusDays(6)
    val previous7Start = last7Cutoff.minusDays(7)
    val previous7End = last7Cutoff.minusDays(1)
    val recent7Count = parsedDates.count { !it.isBefore(last7Cutoff) }
    val previous7Count = parsedDates.count { !it.isBefore(previous7Start) && !it.isAfter(previous7End) }

    items += InsightItem(
      text = when {
        consistency.currentStreak >= 5 ->
          "Check-ins have been steady lately, which gives the app a stronger signal to work from."
        recent7Count > previous7Count ->
          "Your check-in consistency improved this week."
        else ->
          "Your logging pattern is present, but still uneven week to week."
      },
      evidence = "Current streak ${consistency.currentStreak} days; ${recent7Count} check-ins in the past 7 days.",
    )

    val afterGapEntries = mutableListOf<CheckInEntry>()
    val regularEntries = mutableListOf<CheckInEntry>()
    for (i in 1 until sorted.size) {
      val previous = parseDate(sorted[i - 1]) ?: continue
      val current = parseDate(sorted[i]) ?: continue
      val gapDays = Duration.between(previous.atStartOfDay(), current.atStartOfDay()).toDays()
      if (gapDays > 1) {
        afterGapEntries += sorted[i]
      } else {
        regularEntries += sorted[i]
      }
    }

    if (afterGapEntries.size >= 2 && regularEntries.size >= 2) {
      val gapStress = afterGapEntries.map { it.stressLevel }.average()
      val regularStress = regularEntries.map { it.stressLevel }.average()
      val gapCraving = afterGapEntries.map { it.cravingIntensity }.average()
      val regularCraving = regularEntries.map { it.cravingIntensity }.average()

      if (gapStress >= regularStress + 1.0 || gapCraving >= regularCraving + 1.0) {
        items += InsightItem(
          text = "Entries logged after a gap tend to come with more strain.",
          evidence =
            "After-gap days avg stress ${fmt(gapStress.toFloat())} vs ${fmt(regularStress.toFloat())}; cravings ${fmt(gapCraving.toFloat())} vs ${fmt(regularCraving.toFloat())}.",
        )
      }
    }

    if (items.size == 1) {
      items += InsightItem(
        text = consistency.message,
        evidence = "${consistency.missedThisWeek} missed days this week across ${consistency.totalEntries} total entries.",
      )
    }

    return items.take(2)
  }

  fun buildInsightPrompt(entries: List<CheckInEntry>): String {
    val sorted = entries.sortedByDescending { it.date }

    // 5 most recent entries verbatim — gives the model concrete recent signal
    val recentRows = sorted.take(5).joinToString("\n") { e ->
      val triggers = e.triggersList
        .filter { it != TriggerKeys.NONE }
        .joinToString(",") { TriggerKeys.displayLabels[it] ?: it }
        .ifEmpty { "-" }
      "${e.date}|${e.cravingIntensity}|${e.mood}|${e.stressLevel}|${e.socialConnection}|${e.selfEfficacy}|$triggers"
    }

    // 4 rolling 7-day weekly averages, pre-computed on CPU — replaces 25 raw rows
    val today = LocalDate.now()
    val weekRows = (0..3).mapNotNull { week ->
      val weekEnd = today.minusDays(week * 7L)
      val weekStart = weekEnd.minusDays(6)
      val w = sorted.filter { e ->
        val d = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@filter false
        !d.isBefore(weekStart) && !d.isAfter(weekEnd)
      }
      if (w.isEmpty()) null
      else {
        fun avg(f: (CheckInEntry) -> Int) = "%.1f".format(w.map { f(it).toDouble() }.average())
        "$weekEnd|${avg { it.cravingIntensity }}|${avg { it.mood }}|${avg { it.stressLevel }}|${avg { it.socialConnection }}|${avg { it.selfEfficacy }}|${w.size}"
      }
    }.joinToString("\n")

    // Trigger frequency across all entries — replaces per-row trigger columns
    val triggerCounts = mutableMapOf<String, Int>()
    var noTriggerDays = 0
    entries.forEach { e ->
      val meaningful = e.triggersList.filter { it != TriggerKeys.NONE }
      if (meaningful.isEmpty()) noTriggerDays++
      else meaningful.forEach { t -> triggerCounts[t] = (triggerCounts[t] ?: 0) + 1 }
    }
    val triggerStr = buildString {
      triggerCounts.entries.sortedByDescending { it.value }.forEach { (k, v) ->
        append("${TriggerKeys.displayLabels[k] ?: k}:$v ")
      }
      if (noTriggerDays > 0) append("no-trigger-days:$noTriggerDays")
    }.trim().ifEmpty { "none recorded" }

    return """
Field guide: craving/stress (1-10 higher=worse), mood/social/efficacy (1-10 higher=better).

RECENT CHECK-INS — ${minOf(sorted.size, 5)} newest, verbatim (newest first):
DATE|CRAV|MOOD|STRESS|SOCIAL|EFF|TRIGGERS
$recentRows

WEEKLY AVERAGES — pre-computed on-device (newest week first, ${entries.size} total check-ins):
WEEK_END|CRAV|MOOD|STRESS|SOCIAL|EFF|N
$weekRows

TRIGGER FREQUENCY: $triggerStr

Return ONLY this JSON:
{
  "patterns": [
    {"text": "...", "evidence": "..."},
    {"text": "...", "evidence": "..."}
  ],
  "protectiveFactors": [
    {"text": "...", "evidence": "..."},
    {"text": "...", "evidence": "..."}
  ]
}

Rules: patterns=2 cross-field observations (combine ≥2 fields each); protectiveFactors=2 positive correlations; text=1 sentence second person; evidence=cite counts or week ranges; no field codes; no advice.
    """.trimIndent()
  }

  private fun fmt(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(v)

  private fun parseDate(entry: CheckInEntry): LocalDate? = runCatching { LocalDate.parse(entry.date) }.getOrNull()
}
