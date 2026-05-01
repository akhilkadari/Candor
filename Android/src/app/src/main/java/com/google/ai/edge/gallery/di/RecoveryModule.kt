package com.google.ai.edge.gallery.di

import com.google.ai.edge.gallery.data.recovery.LiteRtCompiledEmbeddingRuntime
import com.google.ai.edge.gallery.data.recovery.RecoveryEmbeddingRuntime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecoveryModule {

  @Binds
  @Singleton
  abstract fun bindRecoveryEmbeddingRuntime(
    runtime: LiteRtCompiledEmbeddingRuntime,
  ): RecoveryEmbeddingRuntime
}
