package pl.medidesk.mobile.core.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val TAG = "ExternalUrl"

/**
 * WO-MOB-034 (F2A-011): bezpieczne otwarcie URL pochodzącego z backendu
 * (linki social prelegenta, URL dokumentu zgody) w zewnętrznej przeglądarce.
 *
 * Te wartości wpisują admini w panelu — bez allowlisty schematów złośliwy/przejęty
 * admin mógłby osadzić custom-scheme deep link innej apki (np. `medidesk://...`,
 * `intent:`), a operator klikając zostałby przeniesiony w nieoczekiwany flow.
 *
 * Allowlist: WYŁĄCZNIE `http`/`https`. Każdy inny schemat (lub URL nieparsowalny)
 * jest cicho ignorowany. [ActivityNotFoundException] (brak przeglądarki) łapany —
 * nie wywraca aplikacji.
 *
 * UWAGA: `tel:`/`mailto:` konstruowane lokalnie z zaufanych pól (telefon/email)
 * NIE przechodzą przez ten helper — to świadomie inny, bezpieczny przypadek.
 *
 * @return true jeśli intent został wystartowany; false gdy URL odrzucony/błąd.
 */
fun openExternalUrl(context: Context, url: String?): Boolean {
    if (url.isNullOrBlank()) return false

    val uri: Uri = try {
        Uri.parse(url)
    } catch (_: Exception) {
        Log.w(TAG, "Rejected unparseable external URL")
        return false
    }

    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        Log.w(TAG, "Rejected external URL with non-http(s) scheme: $scheme")
        return false
    }

    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        Log.w(TAG, "No activity to handle external URL")
        false
    }
}
