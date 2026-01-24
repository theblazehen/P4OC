package dev.blazelight.p4oc.di

import dev.blazelight.p4oc.data.repository.EventRepositoryImpl
import dev.blazelight.p4oc.data.repository.FileRepositoryImpl
import dev.blazelight.p4oc.data.repository.MessageRepositoryImpl
import dev.blazelight.p4oc.data.repository.SessionRepositoryImpl
import dev.blazelight.p4oc.domain.repository.EventRepository
import dev.blazelight.p4oc.domain.repository.FileRepository
import dev.blazelight.p4oc.domain.repository.MessageRepository
import dev.blazelight.p4oc.domain.repository.SessionRepository
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
