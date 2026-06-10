package pl.medidesk.mobile.core.sync

/**
 * Port czyszczenia cache'u obrazów przy wylogowaniu — WO-MOB-033 (finding N-2).
 *
 * WHY interfejs w core-sync, a implementacja w module `app`: Coil [coil.ImageLoader]
 * (dysk 50 MB + memory cache) jest providowany wyłącznie w module `app`
 * (`di/CoilModule.kt`), bo to `app` implementuje [coil.ImageLoaderFactory] i trzyma
 * singleton ImageLoadera. [LogoutUseCase] (core-sync) NIE może zależeć od Coila ani od
 * modułu `app` — `app` zależy od core-sync, więc bezpośrednia zależność dałaby cykl.
 *
 * Rozwiązanie jest lustrem wzorca [pl.medidesk.mobile.core.network.SessionWipeHook]
 * (port w niższym module, implementacja + @Binds w wyższym): port mieszka tu (core-sync,
 * przy use casie który go woła), a binding do implementacji opartej o ImageLoadera żyje
 * w `app` (`di/ImageCacheModule.kt`). Dzięki temu cała logika wylogowania zostaje w
 * JEDNYM miejscu ([LogoutUseCase]) i pokrywa wszystkie ścieżki logout (manual/401/auth-fail).
 *
 * Czyszczone treści są quasi-publiczne (zdjęcia prelegentów, branding) — zdjęcia
 * uczestników NIE są ładowane przez Coil (zweryfikowane w gate'cie 2.5, grep MdAsyncImage).
 * To higiena rezydualna po F2A-001 (Room), nie domknięcie tej samej luki.
 */
fun interface ImageCacheCleaner {

    /** Czyści dyskowy i pamięciowy cache obrazów. Implementacja musi być idempotentna. */
    fun clear()
}
