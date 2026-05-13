package pl.medidesk.mobile.feature.addorder.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.medidesk.mobile.feature.addorder.domain.FormFieldDefinition
import pl.medidesk.mobile.feature.addorder.domain.FormFieldType

@Composable
fun ParticipantFieldsForm(
    fields: List<FormFieldDefinition>,
    values: Map<String, String>,
    errors: Map<String, String>,
    onFieldChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    // WO-171: multi-participant support — header "Uczestnik X z N" + breadcrumb dots.
    participantIndex: Int = 0,
    totalParticipants: Int = 1
) {
    val effective = if (fields.isEmpty()) defaultFields else fields
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (totalParticipants > 1) {
            // Header z numeracją "Uczestnik X z N"
            Text(
                "Uczestnik ${participantIndex + 1} z $totalParticipants",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            // Breadcrumb dots
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalParticipants) { i ->
                    val isFilled = i <= participantIndex
                    val dotColor = if (isFilled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.outlineVariant
                    val dotSize = if (i == participantIndex) 10.dp else 8.dp
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(dotSize)
                            .background(color = dotColor, shape = CircleShape)
                    )
                }
            }
        } else {
            Text(
                "Dane uczestnika",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        effective.filter { it.visible }.forEach { f ->
            DynamicFormField(
                field = f,
                value = values[f.id].orEmpty(),
                error = errors[f.id],
                onValueChange = { onFieldChange(f.id, it) }
            )
        }
    }
}

private val defaultFields = listOf(
    FormFieldDefinition("firstName", "Imię", FormFieldType.TEXT, true, true, null, emptyList()),
    FormFieldDefinition("lastName", "Nazwisko", FormFieldType.TEXT, true, true, null, emptyList()),
    FormFieldDefinition("email", "Email", FormFieldType.EMAIL, true, true, null, emptyList()),
    FormFieldDefinition("phone", "Telefon", FormFieldType.TEL, true, false, null, emptyList())
)
