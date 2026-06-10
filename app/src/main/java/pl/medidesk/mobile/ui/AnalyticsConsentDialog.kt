package pl.medidesk.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * GDPR consent dialog — pojawia się przy pierwszym uruchomieniu aplikacji
 * (gdy [analyticsConsentFlow] zwraca null).
 *
 * Dialog jest "nieodrzywalny" — user musi podjąć decyzję.
 * Decyzja jest zapisywana do DataStore i respektowana przez cały cykl życia aplikacji.
 * Można ją zmienić w Ustawieniach → Prywatność.
 */
@Composable
fun AnalyticsConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* nieodrzywalny — wymagana decyzja */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Dane analityczne",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                // WO-MOB-032 (F2B-004): dane są PSEUDONIMOWE (powiązane z identyfikatorem
                // konta operatora przez Analytics.identify), a nie anonimowe — copy musi
                // to oddawać, inaczej zgoda jest podważalna (RODO art. 13).
                Text(
                    text = "Ta aplikacja może zbierać pseudonimowe dane o sposobie korzystania " +
                        "(przeglądane ekrany, zdarzenia nawigacyjne, nagrania sesji), " +
                        "aby poprawiać jej działanie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Zbierane są: zdarzenia techniczne (np. otwarcie ekranu), " +
                        "identyfikator Twojego konta oraz model urządzenia. " +
                        "Dane nie zawierają imion, nazwisk, e-maili ani innych danych " +
                        "uczestników — pola haseł i danych osobowych są zawsze maskowane.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Odmawiam")
                    }

                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Akceptuję")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Możesz zmienić to ustawienie w dowolnym momencie w Ustawieniach → Prywatność.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
