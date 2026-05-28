# WO-MOB-016: Mobile etykiety tagów uczestników zgodne z modelem kanonicznym (desktop)

**Data:** 2026-05-25
**Worker:** Implementer (mobile + backend)
**Stage:** Mobile UX polish — alignment z desktop "Tagi uczestników"
**Priorytet:** Normalny (kosmetyczne + spójność, nie blokuje)
**Status:** ✅ **DONE** (2026-05-28 closure; kod zacommitowany backend `5fa1764` + mobile `27ae602` przed sesją, wypchnięty)

> **Closure note (2026-05-28):** Pre-flight verification wykazała, że WO był już w pełni zaimplementowany i zacommitowany — workera NIE dispatchowano. `ParticipantDetailsScreen` ma kanoniczne labele ✅. **`SponsorDetailScreen` świadomie POZA ZAKRESEM** (user-confirmed): tagi sponsora to słownik firmowy/branżowy, nie `participant_tag` — migracja byłaby błędem kategorii. Szczegóły: [REVIEW-WO-MOB-016](review_notes/REVIEW-WO-MOB-016.md) + [IMPLEMENTATION_REPORT](IMPLEMENTATION_REPORT_WO_MOB_016.md).

## Cel

Etykiety tagów osób w aplikacji mobilnej (chipy typu `przedstawiciel_partnera`, `prelegent`, `uczestnik`) wyświetlają się jako **raw slug** zamiast kanonicznej polskiej etykiety z desktopowego panelu "Tagi uczestników" (`Przedstawiciel partnera`, `Prelegent`, `Uczestnik`). Cel: mobile pokazuje **te same labele i kolory** co desktop, **automatycznie reaguje** na zmiany admina w `Ustawienia globalne → Tagi uczestników` (np. zmiana nazwy / dodanie nowego tagu / dezaktywacja).

## Tło / kontekst

**Desktop źródło prawdy:**
- Tabela DB: `participant_tag_definitions` (migracja 0034, seed 10 wartości).
- Backend endpoint: `GET /admin/api/global-settings/participant-tags` → `{ success, tags: [{ key, label_pl, color_bg, color_text, display_order, is_active }] }`.
- Frontend hook: `frontend/src/hooks/useParticipantTags.ts` (React Query, 5 min staleTime, hardcoded fallback `PARTICIPANT_TAG_DEFAULTS`).
- Frontend badge: `frontend/src/components/ui/ParticipantTagBadge.tsx`.

**Mobile stan obecny:**
- DTO `ParticipantDto.tags: List<String>?` w `simple-event-checkin/core/core-network/.../ResponseDtos.kt:76` — przychodzi tablica raw slugów (`["przedstawiciel_partnera"]`) z backendu (WO-MOB-005 `ARRAY[p.participant_tag]`).
- Renderowanie:
  - `simple-event-checkin/features/feature-participants/.../ParticipantDetailsScreen.kt:304-318` — chip z raw `tag` text (bez label/color lookup).
  - `simple-event-checkin/features/feature-sponsors/.../SponsorDetailScreen.kt:226+` — to samo (lista tagów sponsora).

## Zakres

### Backend (jeden nowy endpoint, lekki)

- `backend/api/mobile.py` — **NEW endpoint** `GET /api/mobile/participant-tags`:
  - Auth: `@require_mobile_auth` (każdy zalogowany mobile user, NIE wymaga `global_settings` permission — to read-only metadata o publicznym charakterze).
  - Reuse `pg_storage.list_participant_tag_definitions(active_only=True)` (już istnieje, użyty przez admin endpoint).
  - Reuse `_serialize_participant_tag_definition()` helper (już istnieje w `admin.py`) — **MOVE** do `pg_storage.py` lub do `mobile.py` jako prywatny helper (zero-zmian semantyki, byte-identical output).
  - Response: `{ success: true, tags: [...] }` — identyczny shape jak admin endpoint, tylko bez `created_at/updated_at` (mobile ich nie potrzebuje).

### Mobile (Android — `simple-event-checkin/`)

- `simple-event-checkin/core/core-network/src/main/java/pl/medidesk/mobile/core/network/dto/ResponseDtos.kt`:
  - NEW `ParticipantTagDefinitionDto(key, label_pl, color_bg, color_text, display_order, is_active)`.
  - NEW `ParticipantTagsResponse(success: Boolean, tags: List<ParticipantTagDefinitionDto>)`.
- `simple-event-checkin/core/core-network/src/main/java/pl/medidesk/mobile/core/network/MobileApiService.kt`:
  - NEW `@GET("api/mobile/participant-tags") suspend fun getParticipantTags(): Response<ParticipantTagsResponse>`.
- NEW `simple-event-checkin/core/core-data/src/main/java/pl/medidesk/mobile/core/data/ParticipantTagsRepository.kt` (lub w `core-domain`):
  - Singleton (`@Singleton` Hilt).
  - In-memory cache `StateFlow<Map<String, ParticipantTagDefinitionDto>>`.
  - `suspend fun refresh()` — fetch + update cache; fail-soft (jeśli network error → użyj hardcoded defaults).
  - `fun labelFor(key: String): String` — zwraca `label_pl` lub fallback do `key.replace('_',' ').replaceFirstChar { it.uppercase() }`.
  - `fun colorFor(key: String): Pair<String, String>` — zwraca `(color_bg, color_text)` jako Tailwind-like classes — mobile musi przemapować na `androidx.compose.ui.graphics.Color` (tabela mapowania `tailwind→Color` w companion object, 10 wartości z seedu).
  - Hardcoded `DEFAULT_TAGS` (mirror seedu DB migracji 0034, 10 wartości — identyczne z `frontend/src/hooks/useParticipantTags.ts:61-72`).
- Hook do refresh:
  - Pierwszy refresh w `MainActivity` / `AppNavHost` `LaunchedEffect(Unit)` (po login).
  - Re-refresh po pull-to-refresh w `ParticipantsScreen` (jeśli już istnieje pull-to-refresh — nie dodawać nowego).
- Display rewrite:
  - `simple-event-checkin/features/feature-participants/.../ParticipantDetailsScreen.kt:304-318` — chip używa `tagsRepository.labelFor(tag)` + `colorFor(tag)`.
  - `simple-event-checkin/features/feature-sponsors/.../SponsorDetailScreen.kt:226+` — to samo.

### Drugi remote `MD_mobile_android` — POZA SCOPE 🛑

User-confirmed 2026-05-25: jedyna aplikacja mobile w scope = `simple-event-checkin/` (kanon per `CLAUDE.md`). `MD_mobile_android` NIE ruszamy w tej sesji ani w tym WO. ZERO zmian, ZERO commitów w `C:\Users\kochn\StudioProjects\MD_mobile_android`.

## Czego NIE ruszać 🛑

- `pg_storage.py` — `list_participant_tag_definitions()` JUŻ istnieje, NIE modyfikować.
- `backend/api/admin.py` — admin endpointy `/api/global-settings/participant-tags/*` (POST/PATCH/DELETE) NIE w zakresie; mobile dostaje tylko READ.
- `database/migrations/` — ZERO nowych migracji. Tabela `participant_tag_definitions` już istnieje.
- `frontend/` — desktop poza scope'em, działa.
- Multi-tag refactor (IDEA-002 "Zunifikować ścieżki przedstawiciel_partnera") — odłożone do osobnego WO post-konferencje. **NIE rusza** struktury `participants.participant_tag` (single column) ani `event_partner_contacts`. Backend nadal zwraca `ARRAY[participant_tag]` (1-element).

## Pliki startowe (research order)

1. `frontend/src/hooks/useParticipantTags.ts` — wzorzec hook + fallback defaults (10 wartości, kolor mapping).
2. `backend/api/admin.py:14681-14702` — admin endpoint `api_list_participant_tag_definitions` + `_serialize_participant_tag_definition`.
3. `backend/api/mobile.py` — wzorce `@require_mobile_auth`, jak inne mobile endpointy są strukturyzowane (np. `mobile_get_partners` ~1597).
4. `simple-event-checkin/core/core-network/.../MobileApiService.kt` + `ResponseDtos.kt` — wzorce Retrofit + Moshi.
5. `simple-event-checkin/features/feature-participants/.../ParticipantDetailsScreen.kt:296-320` — miejsce render chipów.
6. `simple-event-checkin/features/feature-sponsors/.../SponsorDetailScreen.kt:220-240` — drugie miejsce render chipów.

## Ryzyko

- **Tailwind class → Compose Color mapping:** `bg-blue-500/10` + `text-blue-700` to format desktopowy; mobile musi mieć tablicę mapowania. Mitygacja: hardcoded 10 wartości w companion object (te same kolory z `useParticipantTags.ts:61-72`). Risk silent drift jeśli admin zmieni `color_bg` na coś spoza 10 znanych klas → fallback do `Color.Gray`.
- **Cache stale przez 5 min:** desktop ma 5 min staleTime. Mobile: po login pierwszy fetch; potem refresh tylko przy app start. Risk: zmiana admina nie propaguje natychmiast. Mitygacja: docs note + opcjonalny `swipe-to-refresh` w przyszłości.
- **Backend endpoint security:** mobile users mogą widzieć **wszystkie definicje tagów** (nie tylko tych z eventów do których mają dostęp). To OK — tagi są globalne, niewrażliwe, identyczne dla wszystkich admin/mobile usrów.

## Definition of Done ✅

- [ ] Backend: `GET /api/mobile/participant-tags` zwraca `{success, tags: [...]}` ze status 200 dla zalogowanego mobile usera.
- [ ] Backend: endpoint zabezpieczony `@require_mobile_auth` (401 bez tokena).
- [ ] Backend: `py -m py_compile backend/api/mobile.py` PASS.
- [ ] Mobile DTO `ParticipantTagDefinitionDto` + `ParticipantTagsResponse` dodane z `@JsonClass(generateAdapter=true)`.
- [ ] Mobile `ParticipantTagsRepository` z hardcoded `DEFAULT_TAGS` (mirror seedu DB) + Tailwind→Color mapping.
- [ ] `ParticipantDetailsScreen` chip pokazuje `label_pl` (np. "Przedstawiciel partnera" zamiast `przedstawiciel_partnera`) z kolorem z definicji.
- [ ] `SponsorDetailScreen` chipy pokazują labele zamiast slugów.
- [ ] App start triggeruje refresh tagów (po login, raz na sesję).
- [ ] Network error nie blokuje UI — fallback do `DEFAULT_TAGS`.
- [ ] Unknown tag (nieobecny w cache + defaults) → fallback `key.replace('_',' ').replaceFirstChar(uppercase)` zamiast pustego stringa.
- [ ] Gradle `assembleDebug` PASS.
- [ ] Review note `simple-event-checkin/.agents/work_orders/review_notes/REVIEW-WO-MOB-016.md`.

## Test akceptacyjny 🧪

**Backend:**
1. `curl -H "Authorization: Bearer <mobile_jwt>" https://<backend>/api/mobile/participant-tags` → status 200, body `{"success": true, "tags": [{"key":"uczestnik","label_pl":"Uczestnik",...}, ...10 wartości...]}`.
2. Bez tokena: status 401.
3. Admin zmienia label `Przedstawiciel partnera` → `Partner Rep` w UI desktop. Drugi curl mobile zwraca już `"label_pl":"Partner Rep"`.

**Mobile (po build APK + install):**
1. Login → otwórz event → lista uczestników → wybierz osobę z tagiem `przedstawiciel_partnera`.
2. Oczekiwany wynik: chip "**Przedstawiciel partnera**" (label PL) zamiast `przedstawiciel_partnera` (slug), pomarańczowe tło (`bg-orange-500/10` zmapowane na `Color(0xFFFFEDD5)` lub podobny), pomarańczowy text (`text-orange-700` → `Color(0xFFC2410C)`).
3. Wybierz osobę z tagiem `prelegent` → chip "Prelegent" fioletowy.
4. Sponsor detail: chipy tagów branżowych — TE SAME labele co desktop.
5. **Reactivity test:** admin desktop zmienia label `Uczestnik` → `Gość`. Force-close + reopen mobile app + login → chip pokazuje "Gość". (W tej iteracji: tylko po app restart. Pull-to-refresh: poza scope.)
6. **Airplane mode test:** wyłącz network, kill app, restart, login z cached credentials (jeśli wspierane). Tagi nadal renderują się z `DEFAULT_TAGS` fallback (np. "Uczestnik", "Prelegent", "Przedstawiciel partnera" — wartości z migracji 0034 seed).

## Oczekiwany efekt wizualny 🖼️

**Przed:**
```
[ przedstawiciel_partnera ]    ← raw slug, szare neutral tło
```

**Po:**
```
[ Przedstawiciel partnera ]    ← polski label, pomarańczowy bg + text
[ Prelegent ]                  ← fioletowy
[ Uczestnik ]                  ← niebieski
```

Wizualnie chipy mają **te same kolory** co badge'e w desktop `EventParticipants` (`ParticipantTagBadge`).

## Kontrakt API 🔗

**NEW endpoint:**
```
GET /api/mobile/participant-tags
Headers: Authorization: Bearer <mobile_jwt>
Response 200:
{
  "success": true,
  "tags": [
    {
      "key": "uczestnik",
      "label_pl": "Uczestnik",
      "color_bg": "bg-blue-500/10",
      "color_text": "text-blue-700",
      "display_order": 10,
      "is_active": true
    },
    ...
  ]
}
```

Shape **identyczna** z desktop admin endpoint (poza pominięciem `created_at/updated_at` — nice-to-have, ale mobile ich nie potrzebuje; decyzja Implementera: zostawić czy obciąć).

## Format zwrotki

- Lista zmienionych plików (backend + mobile) z opisem jednoliniowym per plik.
- Git diff summary (LOC, commit hashes).
- Screenshot przed/po — `ParticipantDetailsScreen` z tagiem `przedstawiciel_partnera` → po zmianie pokazuje "Przedstawiciel partnera" pomarańczowy.
- Curl output z `/api/mobile/participant-tags` (sanitized, bez tokena).
- Gradle `assembleDebug` output (PASS).
- Propozycja gotcha do `simple-event-checkin/.agents/PROGRESS.md` o Tailwind→Compose Color mapping table.

## Sizing

🟡 **Średni:** ~5 plików backend + mobile (1 endpoint + 1 DTO file + 1 repo file + 2 screen edits + 1 service method). Estymata: 2-3h kodowanie + 1h QA.
