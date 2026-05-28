# WO-MOB-023 — Fix: dashboard firmy (Review360) nie otwiera się z „Moi podopieczni" — brak `event_id` w body

| Pole | Wartość |
|---|---|
| **Scope** | mobile (`simple-event-checkin`) |
| **Typ** | Bugfix |
| **Worker** | worker-debugger |
| **Sizing** | 🟢 mały (3 pliki, 1 warstwa — sieć + UI) |
| **Status** | OPEN → IN PROGRESS |
| **Utworzony** | 2026-05-28 |
| **Powiązane** | WO-SEC-009 (2026-05-15, źródło regresji), BUG-MOB (toast „Nie udało się otworzyć dashboardu") |

---

## 1. Cel

Naprawić otwieranie dashboardu firmy (Review360 / insight360) z ekranu **„Moi podopieczni"** (`MyMenteesScreen`). Klik ikony Analytics przy karcie firmy kończy się toastem **„Nie udało się otworzyć dashboardu"** zamiast otwarcia URL-a w przeglądarce.

## 2. Root cause (potwierdzony lekturą kodu)

- Mobile Retrofit `review360View(accountId)` (`MobileApiService.kt:184-187`) wysyła **POST bez body**.
- Backend `mobile_review360_view` (`backend/api/mobile.py:1302-1355`) po **WO-SEC-009 (2026-05-15)** wymaga dla nie-admina:
  - `event_id` w body **ORAZ** `_mobile_user_has_event_access(payload, event_id)`,
  - inaczej zwraca **`403 {"success": false, "error": "Brak dostepu (event_id wymagane lub admin role)"}`**.
- Opiekun (guardian) na evencie (np. AMOZ Connect Gdańsk) **nie jest globalnym adminem** → backend dostaje pusty `event_id` → **403**.
- Retrofit przy odpowiedzi 403 ma `response.body() == null` (błąd jest w `errorBody()`), więc w `openReview360` (`MyMenteesScreen.kt:281-304`) gałąź `when (body?.error)` trafia do `body?.error ?: "Nie udało się otworzyć dashboardu"` → **generyczny toast**.

## 3. Zakres — pliki do zmiany

1. **`core/core-network/.../dto/ResponseDtos.kt`** — dodać DTO:
   ```kotlin
   @JsonClass(generateAdapter = true)
   data class Review360ViewRequest(
       @Json(name = "event_id") val eventId: String
   )
   ```
   (obok `Review360ViewResponse` / `DeleteCompanyAssignmentRequest` — ten sam wzorzec).

2. **`core/core-network/.../MobileApiService.kt`** (linie 183-187) — dodać `@Body`:
   ```kotlin
   @POST("api/mobile/crm/accounts/{accountId}/review360/view")
   suspend fun review360View(
       @Path("accountId") accountId: Long,
       @Body body: Review360ViewRequest
   ): Response<Review360ViewResponse>
   ```

3. **`features/feature-dashboard/.../MyMenteesScreen.kt`**:
   - `openReview360` (linia 281) — dodać parametr `eventId: String`, przekazać `Review360ViewRequest(eventId)` do `api.review360View(accountId, ...)`.
   - Miejsce wywołania (linia ~500-503) — `viewModel.openReview360(group.crmAccountId, eventId) { url -> uriHandler.openUri(url) }` (`eventId` jest parametrem Composable, linia 358 — w scope).

## 4. Czego NIE ruszać

- **NIE** modyfikować backendu — kontrakt WO-SEC-009 jest poprawny (defense-in-depth przeciw enumeracji CRM). Klient ma się dostosować.
- **NIE** zmieniać logiki autoryzacji ani `_mobile_user_has_event_access`.
- **NIE** dotykać innych endpointów ani ekranu `DashboardScreen.kt` (inny przepływ).
- **NIE** zmieniać DashboardScreen / przepływu check-in.

## 5. Test akceptacyjny

1. **Build:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
2. **Smoke (na urządzeniu, post-build):** zalogowany jako opiekun (nie-admin) na evencie z firmą która ma `review360Status == "done"` → klik ikony Analytics → dashboard otwiera się w przeglądarce (CustomTab) zamiast toastu.
3. **Regresja admin:** admin dalej otwiera dashboard (body z event_id nie przeszkadza — admin omija check, ale event_id jest poprawny).

## 6. Oczekiwany efekt

Klik dashboardu firmy z „Moi podopieczni" otwiera insight360 `?sid=...` w przeglądarce dla opiekunów. Toast „Nie udało się otworzyć dashboardu" znika dla poprawnego przypadku (zostaje tylko dla realnych błędów: `dashboard_timeout` 504, `no_data` 404 — które i tak mają body==null przy non-2xx, follow-up opcjonalny: parsowanie errorBody).

## 7. Kontrakt API (bez zmian backendu)

`POST /api/mobile/crm/accounts/<account_id>/review360/view`
- **Request body (NOWE z klienta):** `{ "event_id": "<eventId>" }`
- **Response 200:** `{ "success": true, "url": "https://insight360-3xrr.onrender.com/view?sid=<sid>" }`
- **Response 403/404/504:** `{ "success": false, "error": "<code>" }`

## 8. Pliki startowe

- `simple-event-checkin/features/feature-dashboard/src/main/java/pl/medidesk/mobile/feature/dashboard/presentation/screen/MyMenteesScreen.kt`
- `simple-event-checkin/core/core-network/src/main/java/pl/medidesk/mobile/core/network/MobileApiService.kt`
- `simple-event-checkin/core/core-network/src/main/java/pl/medidesk/mobile/core/network/dto/ResponseDtos.kt`
- (ref) `backend/api/mobile.py:1302-1355` — kontrakt (read-only)
