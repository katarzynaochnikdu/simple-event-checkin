package pl.medidesk.mobile.feature.addorder.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier
) {
    val effective = if (fields.isEmpty()) defaultFields else fields
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Dane uczestnika",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
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
