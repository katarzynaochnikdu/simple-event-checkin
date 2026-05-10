package pl.medidesk.mobile.feature.auth.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.ui.components.MdAsyncImage
import pl.medidesk.mobile.feature.auth.presentation.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    role: String,
    onLoginSuccess: () -> Unit,
    onMustChangePassword: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.isSuccess, uiState.mustChangePassword) {
        if (uiState.isSuccess) {
            if (uiState.mustChangePassword) onMustChangePassword()
            else onLoginSuccess()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(top = 60.dp, bottom = 48.dp)
            ) {
                // Logo from assets - Increased size by ~15% (180dp -> 210dp)
                MdAsyncImage(
                    model = "file:///android_asset/logo_medidesk.png",
                    contentDescription = "Medidesk Logo",
                    modifier = Modifier
                        .size(210.dp),
                    contentScale = ContentScale.Fit,
                    placeholderColor = androidx.compose.ui.graphics.Color.Transparent
                )

                Spacer(modifier = Modifier.height(0.dp))

                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("E-mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.error != null,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Hasło") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.login() }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.error != null,
                    shape = RoundedCornerShape(12.dp)
                )

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = viewModel::login,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Zaloguj się", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = { showForgotDialog = true }) {
                    Text(
                        "Zapomniałeś hasła?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Forgot password dialog
    if (showForgotDialog) {
        var forgotEmail by remember { mutableStateOf(uiState.email) }

        AlertDialog(
            onDismissRequest = {
                showForgotDialog = false
                viewModel.clearForgotPasswordState()
            },
            title = { Text("Resetowanie hasła") },
            text = {
                Column {
                    if (uiState.forgotPasswordSent) {
                        Text(
                            "Link do resetowania hasła został wysłany na podany adres e-mail.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "Podaj adres e-mail powiązany z Twoim kontem. Wyślemy link do zmiany hasła.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = forgotEmail,
                            onValueChange = { forgotEmail = it },
                            label = { Text("E-mail") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done,
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            isError = uiState.forgotPasswordError != null,
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (uiState.forgotPasswordError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.forgotPasswordError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (uiState.forgotPasswordSent) {
                    TextButton(onClick = {
                        showForgotDialog = false
                        viewModel.clearForgotPasswordState()
                    }) {
                        Text("OK")
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.forgotPassword(forgotEmail) },
                        enabled = !uiState.forgotPasswordLoading
                    ) {
                        if (uiState.forgotPasswordLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Wyślij")
                        }
                    }
                }
            },
            dismissButton = {
                if (!uiState.forgotPasswordSent) {
                    TextButton(onClick = {
                        showForgotDialog = false
                        viewModel.clearForgotPasswordState()
                    }) {
                        Text("Anuluj")
                    }
                }
            }
        )
    }
}
