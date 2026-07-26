---
name: refactor
description: Performs behavior-preserving refactors on specified files or directories following project architecture, AGENTS.md, stack conventions, SOLID, and KISS. Use when the user asks to refactor, clean up, restructure, or improve code quality; says "рефактор", "отрефактори", "/refactor"; or wants to reorganize modules without changing behavior.
---

# Refactor

Refactor **without changing external behavior**. Improve structure, readability, and alignment with project rules — not features or bug fixes in the same pass.

## 1. Resolve scope

Determine **what** to refactor before editing.

| Situation | Action |
|-----------|--------|
| User named file(s) or directory | Use those paths as scope |
| Paths implied by context (open files, recent edits, prior messages) | Confirm scope in one short sentence, then proceed |
| New dialog, no paths, no context | **Ask** which file(s) or directory to refactor — do not guess |

When scope is a directory, include only files relevant to the stated goal — not the whole tree by default.

## 2. Read before changing

Read in this order (skip what does not apply to scope):

1. [`AGENTS.md`](../../../AGENTS.md) — architecture, forbidden patterns, TDD, code style
2. [`.cursor/rules/architecture.mdc`](../../rules/architecture.mdc) — layer boundaries, offline-first, API-first
3. **Android scope:** [`.cursor/rules/kotlin-android.mdc`](../../rules/kotlin-android.mdc) — layers, ViewModel, Compose, Room, repos
4. Surrounding code in scope — naming, patterns, imports, tests
5. Stack docs only when needed (Compose, Electron, Deno, etc.) — prefer existing repo patterns over generic advice
6. For stack-specific checklists, smells, and commands → [reference.md](reference.md)

## 3. Principles

Apply in order of priority:

| Principle | In practice |
|-----------|-------------|
| **Behavior preserved** | Same inputs → same outputs; no API/UX changes unless user asked |
| **KISS** | Simplest structure that fits; no premature abstractions |
| **SOLID** | Single responsibility; depend on abstractions; keep layers inward (`ui → domain → data`) |
| **DRY** | Extract duplication only when it reduces real complexity |
| **YAGNI** | No helpers, interfaces, or indirection for hypothetical futures |
| **Minimal diff** | Touch only scope + required call sites/tests; no drive-by changes |

### Architecture (Tonezen)

- **Clients:** `ui → domain → data`; domain must not import platform SDKs (Android, Electron, Supabase)
- **Backend/indexer:** pure functions + thin IO layer; **split data access by API domain** (one repo module per domain under `db/`, aligned with `routes/` — not a monolithic repository)
- **API changes:** `docs/openapi.yaml` → tests → code (refactor alone does not change the contract)
- **Sync:** audiobook progress syncs; music progress stays local — do not merge these paths
- **Auth:** offline-safe JWT handling — do not add sync exp checks or forced logout
- **Desktop:** tray lifecycle rules unchanged

### Android refactor (read `kotlin-android.mdc` when scope is `apps/android/`)

Priority order when cleaning Android code:

1. **Layer violations** — `domain/` importing Room/Android; ViewModel injecting `CatalogDao`/`ApiClient`/entities
2. **God classes** — split `MainViewModel` by feature; split monolithic `ApiClient` and combined Room files
3. **Mapping at boundary** — move entity↔domain mappers to `data/local/`; repos return domain types
4. **Compose cleanup** — hoist state, remove business logic and hardcoded UI strings from ViewModel
5. **Dead domain code** — wire tested coordinators (e.g. `PlaybackCoordinator`) or do not touch in behavior-preserving pass
6. **Polling → Flow** — replace `while (true)` loops with repository/`PlaybackClient` streams

Target: one ViewModel + `*UiState` per feature under `ui/<feature>/`, one repository per OpenAPI domain under `data/`.

### Forbidden during refactor

Do not introduce patterns listed under **Forbidden** in AGENTS.md (secrets, RLS bypass, music progress sync, desktop quit on close, etc.).

## 4. Workflow

Copy and track:

```
Refactor progress:
- [ ] Scope confirmed
- [ ] Context read (AGENTS.md, surrounding code, tests)
- [ ] Plan: what moves/extracts/renames and why
- [ ] Changes applied (minimal diff)
- [ ] Tests still pass / updated if structure moved
- [ ] Lint clean on touched files
```

### Plan (brief, before edits)

State in 2–4 bullets:

- Current smell or misalignment
- Intended structure after refactor
- Files affected
- Risk (layer violations, public API moves, test gaps)

If the refactor exceeds ~400 lines, split into smaller steps and tell the user.

### Execute

1. Move/extract/rename with consistent naming matching the codebase
2. Update imports and call sites in scope
3. Keep comments sparse — only non-obvious business logic
4. Preserve i18n-ready UI strings; English for code comments
5. **Desktop renderer UI** — Tailwind CSS (see `AGENTS.md`); replace inline `style` with utilities or shared classes in `styles.css` — not ad-hoc CSS files per component
6. **Android UI** — feature-local packages; strings to `strings.xml`; Composables stateless; see [kotlin-android.mdc](../../rules/kotlin-android.mdc)

### Verify

Run targeted checks for touched areas:

```bash
make lint    # or scoped linter for the stack
make test    # or package-specific test command
```

If tests exist for refactored logic, run them. Fix failures before finishing.

## 5. What counts as refactor vs not

| Refactor (this skill) | Out of scope — separate task |
|-----------------------|--------------------------------|
| Extract function/class/module | New feature |
| Rename for clarity | Bug fix |
| Move code to correct layer | API contract change |
| Simplify control flow | Performance optimization |
| Remove dead code | Behavior change |

If behavior must change, stop and confirm with the user.

## 6. Report to user

When done, summarize:

- **Scope:** paths refactored
- **Changes:** structure moves (not a full diff dump)
- **Verified:** lint/tests run (or what was skipped and why)
- **Follow-ups:** optional next steps only if clearly useful

Do not commit unless the user explicitly asks (see [git-commit](../git-commit/SKILL.md) skill).

## Examples

**User:** `/refactor apps/desktop/src/main/downloadManager.ts`

→ Read file + AGENTS.md → extract pure helpers if mixed with IO → keep Electron IO in main layer → run desktop lint/tests.

**User:** `рефактор` (new chat, no files open)

→ Ask: «Какие файлы или директорию нужно отрефакторить?»

**User:** «этот модуль слишком большой» (file open in editor)

→ Scope = open file; confirm in one line; split by responsibility within same layer.

**User:** `/refactor backend/api/src/db.ts`

→ Split into `db/catalog.ts`, `db/downloads.ts`, `db/progress.ts`; inject domain repos via `RouteDeps`; keep LWW and other rules in `lib/`; run `npm test` in `backend/api`.

**User:** `/refactor apps/android`

→ Read `kotlin-android.mdc` + AGENTS.md Android section; fix layer violations first (DAO/entities out of ViewModel); extract repos per domain; split god ViewModel; verify with `./gradlew assembleDebug` only if the user asks.
