# REVIEW-WO-MOB-005 — Backend SELECT extension (5 pól w mobile participants endpoint)

**Data:** 2026-05-19
**Status:** ✅ Code complete, compile PASS, both gates PASS, awaiting commit
**Worker:** worker-implementer (backend)
**Stage:** Backend / Mobile API

## DoD checklist (z WO-MOB-005)

- [✅] `get_participants_for_mobile()` zwraca 5 nowych pól (`phone`, `tags`, `is_walkin`, `buyer_name`, `buyer_email`) — `pg_storage.py:23914-23937`
- [✅] `get_walkin_participants_for_mobile()` zwraca 5 pól z różnymi wartościami (phone z `w.phone`, is_walkin=TRUE, tags/buyer_*=None explicit) — `pg_storage.py:24638-24665`
- [✅] `py -m py_compile backend/pg_storage.py backend/api/mobile.py` — PASS
- [✅] `backend/api/MOBILE_API.md` updated (+21 linii, sekcja "Pola Buyer & Tags WO-MOB-005")
- [✅] `API_CONTRACT.md` updated (+25 linii, sekcja "Mobile Participants — Buyer & Tags Fields WO-MOB-005")
- [⏳] **Test akceptacyjny curl** — DEFERRED post-deploy (`/api/mobile/events/24311000000909074/participants`)
- [⏳] Spot check DB dla phone/tags — DEFERRED post-deploy
- [✅] Security Gate: **PASS** (0 findings; desktop parity 100% potwierdzona — patrz tabela poniżej)
- [✅] Contract Sync Gate: **PASS** (5 pól × 7 warstw spójne; psycopg2 + jsonify array serialization verified)
- [✅] Migration Guard: **N/A** (zero SQL migrations)

## Gates summary

| Gate | Status | Klucz finding |
|---|---|---|
| 🔒 Security | **PASS** | 0 Crit/High/Med, 1 niskie informational (walk-in explicit None — self-documenting contract, dobra praktyka). **PII desktop parity 100% potwierdzona:** wszystkie 5 pól (`phone`, `participant_tag`, `purchaser_email`, `purchaser_first/last_name`) są już w `get_participants_for_event()` desktop admin endpoint. Mobile dodaje TYLKO UI access do już dostępnych danych — zero rozszerzenia uprawnień, ADR nie wymagany. SQL injection N/A (kolumny + literals, brak f-string interpolation user input). Performance N/A (ZERO new JOINs). Error path logging clean (psycopg2 nie wstawia row data do exceptions). |
| 🔗 Contract Sync | **PASS** | 5 pól × 7 warstw spójne (backend SELECT regular + walk-in ↔ MOBILE_API.md ↔ API_CONTRACT.md ↔ mobile DTO ↔ Entity ↔ Domain ↔ mappery). Walk-in vs regular endpoint contract parity przez explicit None pattern. **Pre-existing drift z WO-MOB-004 Contract Sync gate: RESOLVED.** `ARRAY[p.participant_tag]` → JSON `["prelegent"]` weryfikowane (psycopg2 default array adapter + Flask jsonify). |
| 🗄️ Migration Guard | **N/A** | Zero SQL migrations (kolumny istnieją od dawna). |
| 🧪 QA | **DEFERRED** | Wymaga deploy + JWT mobile token + curl test na event `24311000000909074`. Backend code już compile PASS. |

## Closure: Pre-existing drift backend↔DTO (z WO-MOB-004) → **RESOLVED**

Pre-WO-MOB-005 stan (flagowany przez Contract Sync gate WO-MOB-004 jako WARN):
- Mobile DTO miało `phone`, `tags`, `isWalkin`, `buyerName`, `buyerEmail` (od WO-MOB-002/003)
- Backend SELECT NIE zwracał tych 5 pól
- Moshi defaults `null`/`false`/`emptyList` maskowały drift — silent contract gap

Post-WO-MOB-005:
- Backend produkuje wszystkie 5 pól explicit w obu endpointach (regular + walk-in)
- Dokumentacja zaktualizowana w MOBILE_API.md + API_CONTRACT.md
- Backwards compat: additive change, pre-WO-MOB-003 klienci nieaffected, post-WO-MOB-003 dostają prawdziwe dane zamiast Moshi defaults

## Pole-by-pole consistency tabela (z Contract Sync raport)

| Field | Backend SQL (regular) | Backend SQL (walk-in) | Mobile DTO | Mobile Entity | Mobile Domain | Mappery |
|---|---|---|---|---|---|---|
| `phone` | `p.phone` | `w.phone` | `String? = null` | `String?` | `String?` | `phone = phone` (oba) |
| `tags` | `CASE WHEN ... ARRAY[participant_tag] ELSE NULL END` | `d["tags"] = None` | `List<String>? = null` | `String?` (CSV) | `List<String> = emptyList()` | `tags?.joinToString(",")` / `split+filter` |
| `is_walkin` | `FALSE AS is_walkin` | `d["is_walkin"] = True` | `Boolean = false` | `Boolean defaultValue="0"` | `Boolean = false` | `isWalkin = isWalkin` |
| `buyer_name` | `NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(first), ''), NULLIF(TRIM(last), '')))), '')` | `d["buyer_name"] = None` | `String? = null` | `String? = null` | `String? = null` | `buyerName = buyerName` |
| `buyer_email` | `o.purchaser_email` | `d["buyer_email"] = None` | `String? = null` | `String? = null` | `String? = null` | `buyerEmail = buyerEmail` |

**Wszystkie komórki aligned.** Walk-in column różni się semantycznie (None vs FALSE→TRUE) — udokumentowane w MOBILE_API.md + API_CONTRACT.md.

## Findings

### [INFO] Walk-in endpoint explicit None — self-documenting contract

Implementer zdecydował o `d["tags"] = None`, `d["buyer_name"] = None`, `d["buyer_email"] = None` w pętli rows zamiast polegania na Moshi defaults. Dobra praktyka — kod jawnie pokazuje że walk-ini NIE mają tych pól z definicji biznesowej (brak `participants.id` → brak tagu, brak `orders` row → brak purchaser fields). Self-documenting.

### [INFO] Bonus: `phone` z WO-MOB-004 latent bug fix teraz **active in production**

Po WO-MOB-004 unified mapper zawierał `phone = phone` w `toDomain()` (canonical z list view), ale backend nie wysyłał wartości — bug fix invisible w prod. Po WO-MOB-005 backend zwraca `p.phone` → mobile details screen pokaże prawdziwy telefon uczestnika gdy `participants.phone IS NOT NULL`.

## Recommendation

✅ **APPROVED dla commit + push.**

WO osiągnęło cel:
- 5 pól zwracane przez backend (regular + walk-in)
- ZERO new JOINs
- Desktop parity zachowana
- Contract drift z WO-MOB-004 RESOLVED
- Compile PASS
- Both gates PASS
- Backwards compat (additive)
- Bonus: aktywuje phone fix z WO-MOB-004 w produkcji

**Sekwencja domknięcia:**
1. Commit backend submodule (pg_storage.py + MOBILE_API.md)
2. Push backend submodule
3. Commit main repo (API_CONTRACT.md + meta-context + backend submodule bump)
4. Push main repo
5. Render auto-deploy backend (~2-3 min)
6. Post-deploy curl QA na event `24311000000909074` (user-driven)
