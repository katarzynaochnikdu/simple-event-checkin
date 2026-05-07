package pl.medidesk.mobile.feature.participants.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// All dependencies are constructor-injected (ParticipantDao, SyncEngine).
@Module
@InstallIn(SingletonComponent::class)
object ParticipantsModule
