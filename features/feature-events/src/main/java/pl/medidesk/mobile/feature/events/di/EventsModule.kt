package pl.medidesk.mobile.feature.events.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.medidesk.mobile.feature.events.data.repository.EventsRepositoryImpl
import pl.medidesk.mobile.feature.events.domain.repository.EventsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EventsModule {
    @Binds @Singleton
    abstract fun bindEventsRepository(impl: EventsRepositoryImpl): EventsRepository
}
