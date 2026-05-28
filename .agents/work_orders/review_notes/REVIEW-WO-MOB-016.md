# REVIEW — WO-MOB-016: Kanoniczne etykiety tagów uczestników (mobile)

**Data review:** 2026-05-28
**Reviewer:** Master Agent (closure retroaktywny — pre-flight verification)
**Wynik:** ✅ DONE (z 1 świadomym odstępstwem od DoR + uzupełnioną dokumentacją)

---

## Kontekst zamknięcia

WO-MOB-016 był w backlogu oznaczony jako **otwarty** (brak w INDEX.md, brak wpisu w PROGRESS.md, brak review note), ale **pre-flight verification** (lekcja WO-SEC-004: "sprawdź faktyczne pliki + commit log zanim dispatchujesz workera") wykazała, że **kod był już w pełni zaimplementowany, zacommitowany i wypchnięty** przed tą sesją:

| Repo | Commit |
|---|---|
| backend | `5fa1764 feat(WO-MOB-016): GET /api/mobile/participant-tags endpoint` |
| mobile (simple-event-checkin) | `27ae602 feat(WO-MOB-016): canonical participant tag labels + colors` |
| mobile (version bump) | `fff3d06 chore(mobile): bump versionCode 2 / versionName 1.0.1 for WO-MOB-005..016 release` |

Oba submoduły: working tree czysty, `origin/master..master` puste (wypchnięte). **Worker NIE był dispatchowany** — to byłoby powtórzenie skończonej pracy. Ta sesja wykonała wyłącznie zamknięcie śladu (review note + IR + INDEX/PROGRESS + uzupełnienie MOBILE_API.md).

To **kolejny przypadek wzorca "backlog OPEN ale faktycznie DONE"** (po WO-253 w tej samej sesji, i serii WO-SEC z 2026-05-18).

---

## Definition of Done — weryfikacja

| DoD | Stan | Dowód |
|---|---|---|
| Backend `GET /api/mobile/participant-tags` → `{success, tags}` 200 | ✅ | `mobile.py:2691-2715` |
| Endpoint `@require_mobile_token` (401 bez tokena) | ✅ | `mobile.py:2692` |
| `py -m py_compile` backend | ✅ | commit `5fa1764` (CI/lokalnie przy commit) |
| DTO `ParticipantTagDefinitionDto` + `ParticipantTagsResponse` z `@JsonClass` | ✅ | `ResponseDtos.kt:480,492` |
| `ParticipantTagsRepository` z `DEFAULT_TAGS` (10, mirror seedu 0034) + Tailwind→Color | ✅ | `core-sync/ParticipantTagsRepository.kt` (label/default), mapping w `core-ui/ParticipantTagChip.kt` |
| `ParticipantDetailsScreen` chip pokazuje `label_pl` + kolor | ✅ | `ParticipantDetailsScreen.kt:314-318` (`ParticipantTagChip(rawKey, definition=tagDefs[tag])`) |
| `SponsorDetailScreen` chipy pokazują labele zamiast slugów | ❌ → **OUT OF SCOPE** | patrz niżej |
| App start triggeruje refresh (po login) | ✅ | `LoginViewModel.kt:14,34` wstrzykuje + woła `ParticipantTagsRepository` |
| Network error → fallback `DEFAULT_TAGS` | ✅ | `ParticipantTagsRepository.refresh()` fail-soft (try/catch, zostawia cache) |
| Unknown tag → humanized fallback | ✅ | `labelFor()` + `ParticipantTagChip.humanizeKey()` (snake_case → Title Case) |
| Gradle `assembleDebug` PASS | ⚠️ | nie re-weryfikowane w tej sesji; commit version-bump "for ...016 release" implikuje udany build |
| Review note `REVIEW-WO-MOB-016.md` | ✅ | ten plik |

---

## Świadome odstępstwo: SponsorDetailScreen poza zakresem 🛑

WO listował `SponsorDetailScreen.kt:226+` jako drugie miejsce renderowania chipów do migracji. Pre-flight wykazał, że ekran sponsora **nie został zmigrowany** — `SponsorDetailScreen.kt:226-230` nadal renderuje `AssistChip { Text(tag) }` (raw slug).

**Decyzja (user-confirmed 2026-05-28): OUT OF SCOPE.** Rationale:
- Tagi sponsora (`SponsorDetailViewModel.kt:66` `tags = dto.tags.orEmpty()`) to **inny słownik** niż `participant_tag`. Sponsor to **firma**, a model ma osobne pole `industryCategory` (`SponsorDetailViewModel.kt:50`).
- Słownik `participant_tag` (`uczestnik`/`prelegent`/`przedstawiciel_partnera`/...) opisuje **rolę osoby na wydarzeniu** — nie ma sensu dla firmy-sponsora.
- Migracja `SponsorDetailScreen` na `ParticipantTagChip` byłaby **błędem kategorii**: `tagDefs[companyTag]` → `null` → fallback do humanizacji surowego klucza, bez kanonicznych labeli (bo tagi firmowe nie istnieją w `participant_tag_definitions`).

WO scope over-reach. Jeśli w przyszłości chcemy kanoniczne labele dla tagów firmowych — to **osobny słownik + osobny WO**, nie ten.

---

## Uzupełnienia tej sesji (docs only)

- `backend/api/MOBILE_API.md` — dodano dokumentację endpointu `GET /api/mobile/participant-tags` (Krok 6.6; commit `5fa1764` nie zawierał aktualizacji doc).
- `simple-event-checkin/.agents/work_orders/INDEX.md` — dodano wiersz WO-MOB-016 (DONE) + reconcile stale 009/010/012/013 wg PROGRESS.md.
- `simple-event-checkin/.agents/PROGRESS.md` — wpis WO-MOB-016 DONE.
- `IMPLEMENTATION_REPORT_WO_MOB_016.md` — utworzony.

## Bramki

Snapshot / QA / Security / Contract Sync / Migration — **N/A**: ta sesja to wyłącznie zamknięcie dokumentacji (zero zmian kodu/DB/auth). Kod był zwalidowany przy oryginalnych commitach (`5fa1764` / `27ae602`).

## Uwagi / follow-up

- **Pełna reaktywność** (admin zmienia label desktop → mobile reflektuje po app-restart) wymaga **wdrożonego** endpointu backendu. Commit `5fa1764` jest wypchnięty → Render auto-deploy powinien go obsłużyć. Niezależnie: mobile `DEFAULT_TAGS` (mirror seedu) gwarantuje poprawne labele nawet bez działającego endpointu (fail-soft).
- **Gotcha (drift):** `DEFAULT_TAGS` w `ParticipantTagsRepository.kt` to **4-ta** zahardkodowana kopia seedu tagów (obok `database/migrations/0034`, `frontend/src/hooks/useParticipantTags.ts`, oraz tabeli `participant_tag_definitions`). Zmiana seedu = sync w 3+ miejscach kodu. Plus mapa Tailwind→Color w `ParticipantTagChip.kt` (19 wpisów) — jeśli admin doda kolor spoza listy, chip spadnie do szarego fallbacku.
- Build `assembleDebug` nie re-weryfikowany w tej sesji (oparto się na commicie version-bump).
