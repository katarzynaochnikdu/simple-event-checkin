# simple-event-checkin / .agents/

Lokalny workflow spine dla aplikacji **mobile** (Android natywny Kotlin).

## Struktura

```
.agents/
  PROGRESS.md            — chronologiczny log (`/mlog`)
  work_orders/
    INDEX.md
    WO-MOB-NNN-*.md      — Work Ordery mobile (`/mwo`)
  bugs/
    INDEX.md
    BUG-MOB-NNN-*.md     — bugi mobile (`/mbug`)
  ideas/
    INDEX.md
    IDEA-MOB-NNN-*.md    — pomysły mobile (`/midea`)
```

## Relacja do głównego `.agents/`

- Główne `.agents/` (desktop) = kanon całego monorepo (context, plans, workflows, desktop WO).
- To `.agents/` (mobile) = **właśnie ten katalog** — artefakty związane wyłącznie z aplikacją `simple-event-checkin/`.
- Cross-visibility — przez komendy:
  - `/backlog` i `/status` — **merged view** desktop + mobile.
  - `/mbacklog` i `/mstatus` — tylko mobile.
- Konwencje, które obowiązują oba scope'y (security, snake_case backend, formatka WO) — żyją w głównym `.agents/`. Nie duplikujemy ich tutaj.

## Numeracja

- WO mobile: `WO-MOB-001`, `WO-MOB-002`, ... — numeracja niezależna od desktop (`WO-NNN`).
- BUG mobile: `BUG-MOB-001`, `BUG-MOB-002`, ...
- IDEA mobile: `IDEA-MOB-001`, `IDEA-MOB-002`, ...

## Komendy mobile (w `.claude/commands/`)

| Komenda | Co robi |
|---|---|
| `/mwo <opis>` | Nowy WO-MOB-NNN |
| `/mbug <opis>` | Nowy BUG-MOB-NNN (dopyta o repro jeśli brak) |
| `/midea <opis>` | Nowy IDEA-MOB-NNN |
| `/mlog <opis>` | Wpis do `PROGRESS.md` (mobile) |
| `/mbacklog` | Otwarte WO + bugi + ideas (mobile only) |
| `/mstatus` | Co ostatnio robione (mobile only) |
