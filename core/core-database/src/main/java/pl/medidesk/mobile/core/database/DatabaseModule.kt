package pl.medidesk.mobile.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.medidesk.mobile.core.database.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MdDatabase =
        Room.databaseBuilder(context, MdDatabase::class.java, "md_checkin.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideParticipantDao(db: MdDatabase): ParticipantDao = db.participantDao()
    @Provides fun provideOfflineCheckinDao(db: MdDatabase): OfflineCheckinDao = db.offlineCheckinDao()
    @Provides fun provideSyncMetadataDao(db: MdDatabase): SyncMetadataDao = db.syncMetadataDao()
    @Provides fun provideWalkinDao(db: MdDatabase): WalkinDao = db.walkinDao()
    @Provides fun provideTicketClassDao(db: MdDatabase): TicketClassDao = db.ticketClassDao()
}
