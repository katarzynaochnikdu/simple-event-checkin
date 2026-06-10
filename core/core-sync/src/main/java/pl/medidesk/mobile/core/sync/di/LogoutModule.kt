package pl.medidesk.mobile.core.sync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.medidesk.mobile.core.network.SessionWipeHook
import pl.medidesk.mobile.core.sync.LogoutSessionWipeHook
import javax.inject.Singleton

/**
 * Binding portu [SessionWipeHook] (zdefiniowanego w core-network) do implementacji
 * z core-sync — WO-MOB-028. Mieszka tu, bo core-sync widzi obie strony
 * (core-network = interfejs, LogoutUseCase = implementacja), a core-network
 * nie może zależeć od core-sync (cykl).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LogoutModule {

    @Binds
    @Singleton
    abstract fun bindSessionWipeHook(impl: LogoutSessionWipeHook): SessionWipeHook
}
