package pl.medidesk.mobile.feature.scanner.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// ScannerViewModel is @HiltViewModel — no extra module needed.
// CheckinUseCase is constructor-injected.
@Module
@InstallIn(SingletonComponent::class)
object ScannerModule
