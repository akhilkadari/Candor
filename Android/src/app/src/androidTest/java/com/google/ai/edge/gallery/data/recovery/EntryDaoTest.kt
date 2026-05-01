package com.google.ai.edge.gallery.data.recovery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryDaoTest {

  private lateinit var db: RecoveryDatabase
  private lateinit var entryDao: EntryDao
  private lateinit var insightDao: InsightDao

  @Before
  fun setup() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      RecoveryDatabase::class.java,
    ).allowMainThreadQueries().build()
    entryDao = db.entryDao()
    insightDao = db.insightDao()
  }

  @After
  fun teardown() = db.close()

  // ── Entry tests ──────────────────────────────────────────────────────────

  @Test
  fun entry_insertAndRetrieve() = runBlocking {
    val entry = Entry(
      id = 1L,
      timestamp = System.currentTimeMillis(),
      mood = 7,
      sleepQuality = 6,
      stressLevel = 4,
      socialConnection = 5,
      cravingIntensity = 3,
      selfEfficacy = 8,
      note = "Felt okay today",
    )
    entryDao.insert(entry)
    val all = entryDao.getAll()
    assertEquals(1, all.size)
    assertEquals(7, all[0].mood)
    assertEquals(6, all[0].sleepQuality)
  }

  @Test
  fun entry_getLastN_returnsCorrectCount() = runBlocking {
    repeat(5) { i ->
      entryDao.insert(Entry(
        id = i.toLong(),
        timestamp = i.toLong(),
        mood = 5,
        sleepQuality = 7,
        stressLevel = 3,
        socialConnection = 5,
        cravingIntensity = 2,
        selfEfficacy = 7,
        note = "Entry $i",
      ))
    }
    assertEquals(3, entryDao.getLastN(3).size)
  }

  @Test
  fun entry_getHighCravingDays_filtersCorrectly() = runBlocking {
    entryDao.insert(Entry(id = 1L, timestamp = 1L, mood = 5, sleepQuality = 4,
      stressLevel = 8, socialConnection = 2, cravingIntensity = 9, selfEfficacy = 3,
      note = "Bad day"))
    entryDao.insert(Entry(id = 2L, timestamp = 2L, mood = 8, sleepQuality = 8,
      stressLevel = 2, socialConnection = 8, cravingIntensity = 2, selfEfficacy = 9,
      note = "Good day"))
    val results = entryDao.getHighCravingDays(threshold = 7)
    assertEquals(1, results.size)
    assertEquals(9, results[0].cravingIntensity)
  }

  @Test
  fun entry_getSimilarDays_excludesSelf_andMatchesCorrectly() = runBlocking {
    val reference = Entry(id = 10L, timestamp = 10L, mood = 6, sleepQuality = 6,
      stressLevel = 5, socialConnection = 4, cravingIntensity = 5, selfEfficacy = 6,
      note = "Reference")
    val similar = Entry(id = 20L, timestamp = 20L, mood = 7, sleepQuality = 5,
      stressLevel = 4, socialConnection = 6, cravingIntensity = 6, selfEfficacy = 7,
      note = "Similar")
    val different = Entry(id = 30L, timestamp = 30L, mood = 2, sleepQuality = 3,
      stressLevel = 10, socialConnection = 1, cravingIntensity = 10, selfEfficacy = 2,
      note = "Different")
    entryDao.insert(reference)
    entryDao.insert(similar)
    entryDao.insert(different)
    val results = entryDao.getSimilarDays(
      excludeId = reference.id,
      mood = reference.mood,
      stressLevel = reference.stressLevel,
      cravingIntensity = reference.cravingIntensity,
    )
    assertEquals(1, results.size)
    assertEquals(20L, results[0].id)
  }

  @Test
  fun entry_deleteById_removesOnlyTarget() = runBlocking {
    entryDao.insert(Entry(id = 1L, timestamp = 1L, mood = 5, sleepQuality = 6,
      stressLevel = 3, socialConnection = 4, cravingIntensity = 2, selfEfficacy = 8,
      note = "Keep"))
    entryDao.insert(Entry(id = 2L, timestamp = 2L, mood = 6, sleepQuality = 7,
      stressLevel = 4, socialConnection = 5, cravingIntensity = 3, selfEfficacy = 7,
      note = "Delete"))
    entryDao.deleteById(2L)
    val all = entryDao.getAll()
    assertEquals(1, all.size)
    assertEquals(1L, all[0].id)
  }

  @Test
  fun entry_deleteAll_clearsTable() = runBlocking {
    entryDao.insert(Entry(id = 1L, timestamp = 1L, mood = 5, sleepQuality = 6,
      stressLevel = 3, socialConnection = 4, cravingIntensity = 2, selfEfficacy = 8,
      note = "A"))
    entryDao.insert(Entry(id = 2L, timestamp = 2L, mood = 6, sleepQuality = 7,
      stressLevel = 4, socialConnection = 5, cravingIntensity = 3, selfEfficacy = 7,
      note = "B"))
    entryDao.deleteAll()
    assertTrue(entryDao.getAll().isEmpty())
  }

  // ── Insight tests ─────────────────────────────────────────────────────────

  @Test
  fun insight_insertAndRetrieveLatestByType() = runBlocking {
    insightDao.insert(Insight(id = 1L, generatedAt = 100L, type = "daily",
      body = "Your cravings spiked after poor sleep."))
    insightDao.insert(Insight(id = 2L, generatedAt = 200L, type = "daily",
      body = "Stress preceded high craving days."))
    val latest = insightDao.getLatestByType("daily")
    assertEquals(200L, latest?.generatedAt)
    assertEquals("Stress preceded high craving days.", latest?.body)
  }

  @Test
  fun insight_getLatestByType_returnsNullWhenEmpty() = runBlocking {
    val result = insightDao.getLatestByType("weekly")
    assertNull(result)
  }

  @Test
  fun insight_typesAreIsolated() = runBlocking {
    insightDao.insert(Insight(id = 1L, generatedAt = 100L, type = "daily", body = "Daily insight"))
    insightDao.insert(Insight(id = 2L, generatedAt = 200L, type = "craving", body = "Craving insight"))
    val daily = insightDao.getLatestByType("daily")
    val craving = insightDao.getLatestByType("craving")
    val weekly = insightDao.getLatestByType("weekly")
    assertEquals("Daily insight", daily?.body)
    assertEquals("Craving insight", craving?.body)
    assertNull(weekly)
  }

  @Test
  fun insight_deleteAll_clearsTable() = runBlocking {
    insightDao.insert(Insight(id = 1L, generatedAt = 100L, type = "daily", body = "A"))
    insightDao.insert(Insight(id = 2L, generatedAt = 200L, type = "weekly", body = "B"))
    insightDao.deleteAll()
    assertNull(insightDao.getLatestByType("daily"))
    assertNull(insightDao.getLatestByType("weekly"))
  }
}
