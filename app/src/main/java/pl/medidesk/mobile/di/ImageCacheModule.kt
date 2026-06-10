package pl.medidesk.mobile.di

import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.medidesk.mobile.core.sync.ImageCacheCleaner
import javax.inject.Singleton

/**
 * Implementacja portu [ImageCacheCleaner] (zdefiniowanego w core-sync) oparta o
 * Coil [ImageLoader] — WO-MOB-033 (finding N-2).
 *
 * Mieszka w module `app`, bo to jedyny moduł widzący singleton [ImageLoader]
 * (providowany w [CoilModule], `app` implementuje ImageLoaderFactory). core-sync trzyma
 * sam interfejs i nie zależy od Coila — analogicznie do [pl.medidesk.mobile.core.sync.di.LogoutModule],
 * które bind­uje SessionWipeHook (port w core-network) do implementacji z core-sync.
 *
 * Używamy @Provides (a nie @Binds), bo ImageLoader jest zależnością *dostarczaną* przez
 * Hilt, a nie klasą z @Inject-konstruktorem — nie da się jej zbindować przez parametr typu.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageCacheModule {

    // DiskCache.clear() / MemoryCache.clear() są w Coil 2.7.0 oznaczone
    // @ExperimentalCoilApi (cała powierzchnia DiskCache/MemoryCache jest tak otagowana,
    // mimo że stabilna w praktyce) — świadomy opt-in, bez zmiany zachowania.
    @OptIn(ExperimentalCoilApi::class)
    @Provides
    @Singleton
    fun provideImageCacheCleaner(imageLoader: ImageLoader): ImageCacheCleaner =
        ImageCacheCleaner {
            // clear() jest synchroniczne i bezpieczne do wywołania spoza main thread
            // (LogoutUseCase woła je z bloku NonCancellable na Dispatchers.IO).
            // Operatory bezpieczne na null — diskCache/memoryCache są opcjonalne w Coil.
            imageLoader.diskCache?.clear()
            imageLoader.memoryCache?.clear()
        }
}
