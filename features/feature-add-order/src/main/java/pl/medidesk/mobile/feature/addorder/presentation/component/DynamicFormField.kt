package pl.medidesk.mobile.feature.addorder.presentation.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import pl.medidesk.mobile.feature.addorder.domain.FormFieldDefinition
import pl.medidesk.mobile.feature.addorder.domain.FormFieldType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFormField(
    field: FormFieldDefinition,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val labelText = if (field.required) "${field.label} *" else field.label
    val supportText: @Composable (() -> Unit)? = error?.let { { Text(it) } }

    when (field.type) {
        FormFieldType.SELECT -> {
            var expanded by remember { mutableStateOf(false) }
            val displayValue = field.options.find { it.value == value }?.label ?: value
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = displayValue,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(labelText) },
                    isError = error != null,
                    supportingText = supportText,
                    placeholder = field.placeholder?.let { { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    field.options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.label) },
                            onClick = { onValueChange(opt.value); expanded = false }
                        )
                    }
                }
            }
        }
        FormFieldType.TEXTAREA -> {
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                label = { Text(labelText) },
                isError = error != null,
                supportingText = supportText,
                placeholder = field.placeholder?.let { { Text(it) } },
                maxLines = 4,
                modifier = modifier.fillMaxWidth()
            )
        }
        else -> {
            val keyboardType = when (field.type) {
                FormFieldType.EMAIL -> KeyboardType.Email
                FormFieldType.TEL -> KeyboardType.Phone
                else -> KeyboardType.Text
            }
            // Email pole nie powinno auto-capitalize'ować pierwszej litery (irytujące przy wpisywaniu).
            val capitalization = if (field.type == FormFieldType.EMAIL) KeyboardCapitalization.None
                else KeyboardCapitalization.Sentences
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                label = { Text(labelText) },
                isError = error != null,
                supportingText = supportText,
                placeholder = field.placeholder?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    capitalization = capitalization,
                    autoCorrect = field.type != FormFieldType.EMAIL
                ),
                modifier = modifier.fillMaxWidth()
            )
        }
    }
}
