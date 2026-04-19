package com.docly.app.app.di

import com.docly.app.core.common.IdProvider
import com.docly.app.core.common.UuidIdProvider
import com.docly.app.core.dispatchers.DefaultDispatcherProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.AndroidAppFileDirectories
import com.docly.app.core.file.AppFileDirectories
import com.docly.app.core.logging.AndroidAppLogger
import com.docly.app.core.logging.AppLogger
import com.docly.app.core.time.SystemTimeProvider
import com.docly.app.core.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindIdProvider(impl: UuidIdProvider): IdProvider

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindAppLogger(impl: AndroidAppLogger): AppLogger

    @Binds
    @Singleton
    abstract fun bindAppFileDirectories(impl: AndroidAppFileDirectories): AppFileDirectories
}
