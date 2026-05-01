package com.google.ai.edge.gallery.data

import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.proto.CheckInCollection
import com.google.ai.edge.gallery.proto.CheckInEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

interface CheckInRepository {
  fun addOrReplaceEntry(entry: CheckInEntry)
  fun getAllEntries(): List<CheckInEntry>
  fun getRecentEntries(days: Int): List<CheckInEntry>
  fun deleteEntry(date: String)
  fun getEntryCount(): Int
  fun clearAllEntries()
}

class DefaultCheckInRepository(
  private val dataStore: DataStore<CheckInCollection>,
) : CheckInRepository {

  override fun addOrReplaceEntry(entry: CheckInEntry) {
    runBlocking {
      dataStore.updateData { current ->
        val filtered = current.entriesList.filter { it.date != entry.date }
        current.toBuilder()
          .clearEntries()
          .addAllEntries(listOf(entry) + filtered)
          .build()
      }
    }
  }

  override fun getAllEntries(): List<CheckInEntry> = runBlocking {
    dataStore.data.first().entriesList.sortedByDescending { it.date }
  }

  override fun getRecentEntries(days: Int): List<CheckInEntry> {
    val cutoff = LocalDate.now().minusDays(days.toLong()).toString()
    return getAllEntries().filter { it.date >= cutoff }
  }

  override fun deleteEntry(date: String) {
    runBlocking {
      dataStore.updateData { current ->
        current.toBuilder()
          .clearEntries()
          .addAllEntries(current.entriesList.filter { it.date != date })
          .build()
      }
    }
  }

  override fun getEntryCount(): Int = runBlocking {
    dataStore.data.first().entriesCount
  }

  override fun clearAllEntries() {
    runBlocking {
      dataStore.updateData { it.toBuilder().clearEntries().build() }
    }
  }
}
