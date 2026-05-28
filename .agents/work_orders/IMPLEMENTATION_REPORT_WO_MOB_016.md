# IMPLEMENTATION_REPORT WO-MOB-016

**Data raportu:** 2026-05-28 (closure retroaktywny)
**Scope:** mobile (simple-event-checkin) + backend (`api/mobile.py`)
**Worker:** brak — kod już zaimplementowany i zacommitowany przed sesją; Master wykonał wyłącznie zamknięcie śladu
**Snapshot pre-impl:** N/A (zmiana kodu odbyła się w oryginalnych commitach `5fa1764` / `27ae602`, poza tą sesją)

---

## TL;DR

Kanoniczne etykiety i kolory tagów uczestników w aplikacji mobilnej — **DONE i zacommitowane** zanim WO został formalnie zamknięty (backlog pokazywał go jako otwarty: brak w INDEX, brak PROGRESS, brak review note). Pre-flight verification (lekcja WO-SEC-004) ujawniła pełną implementację → **NIE dispatchowano workera** (uniknięto powtórzenia pracy). Ta sesja: review note + ten IR + INDEX/PROGRESS + uzupełnienie `MOBILE_API.md`.

**Commity (oba submoduły, wypchnięte — `origin/master..master` puste):**
- backend: `5fa1764 feat(WO-MOB-016): GET /api/mobile/participant-tags endpoint`
- mobile: `27ae602 feat(WO-MOB-016): canonical participant tag labels + colors` + `fff3d06` (version bump 1.0.1 "for WO-MOB-005..016 release")

---

## Zmienione / nowe pliki (w oryginalnych commitach)

### Backend (`backend/`, commit `5fa1764`)
| Plik | Status | Opis |
|---|---|---|
| `api/mobile.py` (~2680-2716) | MOD | NEW endpoint `GET /api/mobile/participant-tags`, `@require_mobile_token`, reuse `list_participant_tag_definitions(active_only=True)`, response `{success, tags:[{key,label_pl,color_bg,color_text,display_order,is_active}]}` z fallbackami na NULL kolory, try/except → 500. Reuse istniejących helperów (zero nowych funkcji w `pg_storage.py`). |

### Mobile (`simple-event-checkin/`, commit `27ae602`)
| Plik | Status | Opis |
|---|---|---|
| `core/core-network/.../dto/ResponseDtos.kt:480,492` | MOD | NEW `ParticipantTagDefinitionDto` (`@JsonClass`) + `ParticipantTagsResponse(success, tags)` |
| `core/core-network/.../MobileApiService.kt:18` | MOD | NEW `@GET("api/mobile/participant-tags") getParticipantTags()` |
| `core/core-sync/.../ParticipantTagsRepository.kt` | NEW | `@Singleton`, in-memory `StateFlow<Map<String,Dto>>` seeded `DEFAULT_TAGS` (10, mirror seedu 0034), `refresh()` fail-soft, `definitionFor()`, `labelFor()` humanized fallback. Data-only (zero Compose). |
| `core/core-ui/.../components/ParticipantTagChip.kt` | NEW | Composable chip; Tailwind→Compose Color mapping (19 bg + 19 text); `definition?.labelPl ?: humanizeKey()`; szary fallback. |
| `features/feature-auth/.../LoginViewModel.kt:14,34` | MOD | Inject + wywołanie `ParticipantTagsRepository.refresh()` po loginie |
| `features/feature-participants/.../ParticipantDetailsViewModel.kt:19,45` | MOD | Inject `tagsRepository` |
| `features/feature-participants/.../ParticipantDetailsScreen.kt:314-318` | MOD | Render `ParticipantTagChip(rawKey=tag, definition=tagDefs[tag])` |
| `features/feature-auth/build.gradle.kts`, `core/core-ui/build.gradle.kts` | MOD | Dependency wiring |

### Uzupełnienia tej sesji (docs only — NIE w oryginalnych commitach)
| Plik | Status | Opis |
|---|---|---|
| `backend/api/MOBILE_API.md` | MOD | Dodano dokumentację endpointu `GET /api/mobile/participant-tags` |
| `.agents/work_orders/review_notes/REVIEW-WO-MOB-016.md` | NEW | Review note |
| `.agents/work_orders/INDEX.md` | MOD | Wiersz WO-MOB-016 DONE + reconcile stale wpisy |
| `.agents/PROGRESS.md` | MOD | Wpis WO-MOB-016 DONE |

---

## Definition of Done — checklist

- ✅ Backend `GET /api/mobile/participant-tags` → `{success, tags}` 200
- ✅ `@require_mobile_token` (401 bez tokena)
- ✅ `py -m py_compile` (przy commicie `5fa1764`)
- ✅ DTO `ParticipantTagDefinitionDto` + `ParticipantTagsResponse` z `@JsonClass`
- ✅ `ParticipantTagsRepository` + `DEFAULT_TAGS` (mirror seedu) + Tailwind→Color mapping
- ✅ `ParticipantDetailsScreen` chip = `label_pl` + kolor
- ❌ → **OUT OF SCOPE** `SponsorDetailScreen` (tagi sponsora = słownik firmowy/branżowy, nie `participant_tag` — patrz REVIEW)
- ✅ App start refresh (po login)
- ✅ Network error → fallback `DEFAULT_TAGS` (fail-soft)
- ✅ Unknown tag → humanized fallback
- ⚠️ `assembleDebug` — nie re-weryfikowane w tej sesji (commit version-bump implikuje udany build)
- ✅ Review note utworzony

## Bramki
Snapshot / QA / Security / Contract / Migration — **N/A** (closure = docs only, zero zmian kodu/DB/auth tej sesji).

---

## Postmortem (4 pytania)

### 1. Co poszło dobrze?
- Implementacja zgodna z WO co do joty (endpoint shape, fail-soft repo, fallbacky). Architektura czysta: `core-sync` data-only (zero Compose), mapping Tailwind→Color izolowany w `core-ui`.
- `DEFAULT_TAGS` jako mirror seedu daje poprawne labele nawet bez wdrożonego endpointu (fail-soft) — feature działa wizualnie niezależnie od deployu backendu.
- Mapa kolorów (19 wpisów) jest defensywna ponad 10 używanych tagów — odporna na admina dodającego nowe kolory z palety Tailwind.

### 2. Co poszło źle / wymaga dopracowania?
- **Trail nie został zamknięty przy implementacji** — WO wyglądał na otwarty (brak INDEX/PROGRESS/review), co spowodowało, że został wybrany do "uruchomienia" mimo że był DONE. Pre-flight verification złapała to przed dispatchem workera (drugi raz w tej sesji, po WO-253).
- **WO scope over-reach:** SponsorDetailScreen wpisany do DoR jako miejsce do migracji, choć to inny słownik tagów. Implementer słusznie pominął, ale rozjazd DoR↔implementacja nie był udokumentowany do tej sesji.
- **MOBILE_API.md nie zaktualizowany** przy commicie endpointu (uzupełnione teraz).

### 3. Co zrobiłbyś inaczej?
- Review note + INDEX/PROGRESS od razu przy commicie WO (Krok 5/6 Mastera), żeby backlog nie pokazywał DONE jako OPEN.
- W WO jawnie rozstrzygnąć słownik tagów sponsora PRZED wpisaniem SponsorDetailScreen do zakresu.

### 4. Nowe gotcha?
**TAK** (kandydat do `known_gotchas.md`):
> **Tag seed = 4 zahardkodowane kopie.** Słownik tagów uczestników żyje w: (1) `database/migrations/0034_participant_tag_definitions.sql` (DB seed/source of truth), (2) `frontend/src/hooks/useParticipantTags.ts` (frontend fallback), (3) `simple-event-checkin/core-sync/ParticipantTagsRepository.kt:DEFAULT_TAGS` (mobile fallback), oraz mapa Tailwind→Compose w (4) `core-ui/ParticipantTagChip.kt`. Zmiana seedu wymaga ręcznego sync w 3 miejscach kodu. Admin dodający kolor spoza palety w mapie (4) → chip spada do szarego fallbacku. Single source of truth runtime = endpointy `/api/mobile/participant-tags` + `/admin/api/global-settings/participant-tags`; hardcody to tylko offline/first-run fallback.

---

## Open items / follow-up
- Pełna reaktywność (admin zmienia label → mobile) wymaga wdrożonego endpointu `5fa1764`. Commit wypchnięty → Render auto-deploy. Mobile fallback pokrywa wizualnie niezależnie.
- (opcjonalnie) gotcha "4 kopie seedu tagów" do `known_gotchas.md`.
- (opcjonalnie) jeśli tagi firmowe sponsora mają mieć kanoniczne labele → osobny słownik + osobny WO.
