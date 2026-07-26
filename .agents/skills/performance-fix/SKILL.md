---
name: performance-fix
description: Analyzes code for performance and optimization problems and applies fixes with minimal safe diffs. Use when the user asks to optimize, fix slowness, reduce memory or CPU use, says "/performance-fix", "optimization", "оптимизация", "производительность", "тормозит", or wants remediation beyond profiling advice only.
---

# Performance Fix

Find performance issues in scope, fix them, verify behavior, and report. This skill **changes code** — not read-only profiling notes.

## 1. Resolve scope

| Situation | Action |
|-----------|--------|
| User named file(s) or directory | Use those paths |
| User described a symptom (slow screen, high CPU, jank) | Scope = related feature path + hot path call chain |
| User asked to fix findings from a prior review | Scope = reported locations + callers |
| Open files / recent edits imply scope | Confirm in one sentence, then proceed |
| No scope | **Ask** what is slow or what to optimize — default to changed files on current branch |

Do not optimize the entire monorepo unless the user asked for a full pass.

## 2. Read before changing

1. [`AGENTS.md`](../../../AGENTS.md) — architecture, offline-first, forbidden patterns
2. [`.cursor/rules/architecture.mdc`](../../rules/architecture.mdc) — layer boundaries
3. **Android scope:** [`.cursor/rules/kotlin-android.mdc`](../../rules/kotlin-android.mdc) — coroutines, Flow, Compose
4. Stack checklist in [reference.md](reference.md) for paths in scope
5. Surrounding code and existing tests

## 3. Discovery

Copy and track:

```
Performance fix progress:
- [ ] Scope confirmed
- [ ] Hot path identified (user action → code path)
- [ ] Issues triaged (impact, frequency)
- [ ] Fixes applied (minimal diff, behavior preserved)
- [ ] Tests updated if behavior-critical paths changed
- [ ] Lint/tests run on touched areas
```

### Establish the hot path

Before micro-optimizing, name the slow scenario:

- Which screen / endpoint / job?
- Cold start, scroll, playback, sync, indexer scan?
- Data size (library size, track count, network)?

Prefer fixing **root causes** (N+1 queries, main-thread IO, polling loops) over cosmetic tweaks.

### Search scope for common smells

See [reference.md](reference.md) for stack lists. High-signal patterns:

- Main-thread or UI-thread blocking (sync DB, HTTP, disk, JWT check at cold start)
- `while (true)` / tight polling in ViewModel or renderer
- N+1 SQL or HTTP (loop with query per row)
- Unbounded recomposition / re-renders (missing keys, unstable lambdas, broad state)
- Redundant work every frame or every tick (layout, decode, hash, sign)
- Missing indexes for frequent filters
- Loading entire tables into memory when paging/streaming exists
- Duplicate network calls (no cache, no debounce on search)
- Indexer/API signing every path serially when batching is possible
- Large bitmap/image decode on main thread (Android)
- Electron renderer doing heavy sync SQLite or FS work

**Measure when uncertain** — add timing logs or use existing profilers briefly; remove debug timing before finish unless user asked to keep it.

## 4. Triage

Sort by user-visible impact:

| Impact | Examples | Fix priority |
|--------|----------|--------------|
| **Critical** | Main-thread blocking causing ANR/jank; O(n²) on library load | Fix first |
| **High** | N+1 on hot API; polling every second; full re-render on scroll | Fix in this pass |
| **Medium** | Missing index; redundant decode; uncached repeated reads | Fix if in scope |
| **Low** | Micro-opts, premature struct changes | Note or skip |

If a fix trades memory for speed (or vice versa), state the trade-off briefly.

## 5. Fix principles

| Principle | In practice |
|-----------|-------------|
| **Behavior preserved** | Same UX unless user asked for perf trade-offs |
| **Minimal diff** | Fix the bottleneck; no drive-by refactors |
| **Root cause first** | Algorithm/IO placement before caching layers |
| **Match repo patterns** | Flow/suspend on Android; hooks + memo in React; batched SQL in backend |
| **No forbidden patterns** | AGENTS.md — no sync music progress, no sync JWT exp on cold start main thread, no blocking offline logout |
| **Measure after** | Sanity-check hot path is lighter (fewer queries, off main thread) |

### Tonezen-specific guardrails

- **Android:** IO via `suspend` / `Flow`; no `while (true)` polling in ViewModel — use repo streams or `PlaybackClient`
- **Offline-first:** cache locally first; do not add blocking network gates on cold start
- **Audiobook vs music:** do not merge progress sync paths to “optimize” — music stays local only
- **Desktop:** tray/close behavior unchanged unless perf fix requires it
- **API:** thin routes; batch queries in `db/` repos; pure helpers in `lib/`
- **Indexer:** probe/decode is expensive — avoid duplicate ffprobe per unchanged file when scope allows cheap skip

## 6. Verify

Run targeted checks:

```bash
make lint    # or scoped package lint
make test    # or package-specific tests
```

Android: `./gradlew assembleDebug` when Kotlin paths changed and the user asks for verification (no unit-test suite).

Confirm:

- Hot path no longer blocks UI/main thread for the fixed scenario
- Query/call count reduced where targeted (e.g. single JOIN vs N loops)
- No new linter regressions on touched files

Full benchmarking is optional unless the user asked for numbers.

## 7. Report

**Scope** — paths and scenario optimized

**Fixed** — table sorted by impact:

| Impact | Location | Issue | Fix |
|--------|----------|-------|-----|
| High | `path:line` | Short bottleneck | Short remediation |

**Trade-offs** — memory, latency, complexity (if any)

**Verified** — lint/tests run (or skipped and why)

**Deferred** — out of scope or needs profiling infra — one-line reason

Do not commit unless the user explicitly asks (see [git-commit](../git-commit/SKILL.md)).

## Examples

**User:** `/performance-fix backend/api`

→ Find N+1 in catalog routes/repos → batch SQL → tests → `cd backend/api && npm test` → report.

**User:** «тормозит список библиотеки на Android»

→ Scope library UI + ViewModel + repo → check main thread, Flow collection, Compose stability → fix → unit tests → report.

**User:** «оптимизация indexer»

→ Scope scan/upsert path → reduce redundant probes, batch DB writes where safe → indexer tests → report.

## Additional resources

- Stack checklists and smells → [reference.md](reference.md)
- Structure-only cleanup without perf goal → use [refactor](../refactor/SKILL.md) skill instead
