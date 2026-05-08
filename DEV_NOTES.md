# DEV NOTES — rzeczy do usunięcia przed release

## ⚠️ Hardcoded login credentials

**Plik:** `features/feature-auth/src/main/java/pl/medidesk/mobile/feature/auth/presentation/viewmodel/LoginViewModel.kt`

**Co zrobić przed releasem / po usunięciu konta testowego:**

Zmień w `LoginUiState` z powrotem na puste stringi:

```kotlin
// PRZED (dev prefill — usunąć!)
data class LoginUiState(
    val email: String = "testapki@medidesk.pl",
    val password: String = "V3Xfhkp0sqTA",
    ...
)

// PO (produkcja)
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    ...
)
```

**Kiedy:** gdy konto `testapki@medidesk.pl` zostanie usunięte z systemu.

---
