package pl.medidesk.mobile.core.ui.components

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import pl.medidesk.mobile.core.ui.BuildConfig

/**
 * WO-MOB-034 (N-3): ustawia `FLAG_SECURE` na OKNIE bieżącego Compose
 * `Dialog`/`ModalBottomSheet`, żeby treść okna była zablokowana przed
 * zrzutami ekranu / nagrywaniem / podglądem w "Ostatnich".
 *
 * Activity-level `FLAG_SECURE` (WO-MOB-030, MainActivity) NIE dziedziczy się na
 * osobne okna, które `Dialog`/`ModalBottomSheet` tworzą poza oknem Activity —
 * stąd ten helper musi być wywołany WEWNĄTRZ content-lambdy każdego okna z PII
 * (formularze, dane uczestnika, hasła).
 *
 * Gated `!BuildConfig.DEBUG`: w release okna są chronione; w debug zostają
 * otwarte dla QA/screenshotów (ten sam kierunek co MainActivity z WO-MOB-030).
 *
 * Mechanika: w drzewie Compose okna `Dialog`/`ModalBottomSheet` rodzic
 * `LocalView` implementuje [DialogWindowProvider], który wystawia natywne
 * [android.view.Window] tego okna. `DisposableEffect` zdejmuje flagę po
 * zamknięciu okna (czyszczenie — flaga jest per-window, ale jawny clear chroni
 * przed recyklingiem dekoracji okna).
 */
@Composable
fun SecureDialogEffect() {
    if (BuildConfig.DEBUG) return

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
