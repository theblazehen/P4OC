package com.pocketcode.di

import com.pocketcode.data.repository.EventRepositoryImpl
import com.pocketcode.data.repository.FileRepositoryImpl
import com.pocketcode.data.repository.MessageRepositoryImpl
import com.pocketcode.data.repository.SessionRepositoryImpl
import com.pocketcode.domain.repository.EventRepository
import com.pocketcode.domain.repository.FileRepository
import com.pocketcode.domain.repository.MessageRepository
import com.pocketcode.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    @Singleton
    abstract fun bindEventRepository(impl: EventRepositoryImpl): EventRepository
}
