package pl.medidesk.mobile.feature.addorder.presentation.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.medidesk.mobile.feature.addorder.domain.OrderConsentsConfig

@Composable
fun ConsentsForm(
    consentsConfig: OrderConsentsConfig,
    values: Map<String, Boolean>,
    errors: Map<String, String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Zgody",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        if (consentsConfig.checkboxes.isEmpty() && consentsConfig.infoBlocks.isEmpty()) {
            Text("Brak zgód do zaakceptowania.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        consentsConfig.checkboxes.forEach { consent ->
            val checked = values[consent.id] == true
            val errorKey = "consent_${consent.id}"
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(consent.id, !checked) },
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle(consent.id, it) })
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            (if (consent.required) "* " else "") + consent.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!consent.url.isNullOrBlank()) {
                            Text(
                                "Zobacz dokument",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(consent.url))
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
                if (errors[errorKey] != null) {
                    Text(errors[errorKey]!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 48.dp))
                }
            }
        }

        consentsConfig.infoBlocks.forEach { block ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!block.title.isNullOrBlank()) {
                        Text(block.title, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                    }
                    if (!block.content.isNullOrBlank()) {
                        Text(block.content, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
