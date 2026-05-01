package com.google.ai.edge.gallery.di

import android.content.Context
import com.google.ai.edge.gallery.data.recovery.EntryDao
import com.google.ai.edge.gallery.data.recovery.EntryEmbeddingDao
import com.google.ai.edge.gallery.data.recovery.InsightDao
import com.google.ai.edge.gallery.data.recovery.RecoveryAnalysisStateDao
import com.google.ai.edge.gallery.data.recovery.RecoveryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun provideRecoveryDatabase(@ApplicationContext context: Context): RecoveryDatabase =
    RecoveryDatabase.getInstance(context)

  @Provides
  @Singleton
  fun provideEntryDao(db: RecoveryDatabase): EntryDao = db.entryDao()

  @Provides
  @Singleton
  fun provideInsightDao(db: RecoveryDatabase): InsightDao = db.insightDao()

  @Provides
  @Singleton
  fun provideEntryEmbeddingDao(db: RecoveryDatabase): EntryEmbeddingDao = db.entryEmbeddingDao()

  @Provides
  @Singleton
  fun provideRecoveryAnalysisStateDao(
    db: RecoveryDatabase,
  ): RecoveryAnalysisStateDao = db.recoveryAnalysisStateDao()
}
