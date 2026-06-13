# Tonezen Brandbook

Tonezen is an offline-first media player for audiobooks and music. The product should feel calm, focused, and reliable: a personal media library that keeps working when the network disappears.

## Source Of Truth

**The shipped Android app is the visual reference.** When this document and the app disagree, follow the app.

Canonical implementation:

| Area | Location |
| --- | --- |
| Color tokens | `apps/android/app/src/main/java/com/tonezen/app/ui/theme/TonezenColors.kt` |
| Layout tokens | `apps/android/app/src/main/java/com/tonezen/app/ui/theme/TonezenLayout.kt` |
| Material theme | `apps/android/app/src/main/java/com/tonezen/app/ui/theme/TonezenTheme.kt` |
| Shared components | `apps/android/app/src/main/java/com/tonezen/app/ui/components/` |
| User-facing copy (Russian) | `apps/android/app/src/main/res/values/strings.xml` |

Desktop and marketing should map to the same tokens. Do not invent palette or layout values outside this contract.

## Product Principles

- Offline first: downloaded content and local playback feel first-class.
- Sync is visible, not noisy: audiobook progress and catalog state use small chips and status rows.
- Music stays local: the UI must not imply server progress sync for music playback.
- Dense but calm: screens support real playback workflows without becoming a dashboard.
- Android first: layouts follow Material 3 / Compose patterns, with touch targets of at least 48 dp.

## Brand Personality

Tonezen is quiet, capable, and slightly premium. It should look like a serious player for people who listen for long sessions, not like a social music app.

- Calm: dark surfaces, restrained animation, minimal copy.
- Trustworthy: explicit offline/sync states and clear download controls.
- Focused: playback and library browsing stay central.
- Warm: amber highlights active listening state; teal carries interaction and progress.

## Visual Direction

The app uses a dark media-player system with soft contrast, frosted glass chrome, teal interaction states, and amber listening accents.

- Screen backgrounds use a vertical slate gradient (`#070B0F` → `#020617` → `#071016`) on auth; library/profile use flat `#0f172a` with scroll content under glass chrome.
- Top chrome, bottom chrome, sheets, and dialogs use thin haze blur (24 dp) over raised surfaces with a white 12% border — not heavy glassmorphism.
- Primary actions and playback controls use teal gradients; icons on filled controls use `#020617`.
- Timeline and mini-player progress use teal fills on a white 16% track.
- Active chapter/track rows, playing indicators, and offline warnings use amber tints.
- Avoid purple hero gradients, decorative marketing blobs, and viewport-scaled typography.

## Core Palette

Values match `TonezenColors.kt` unless noted.

| Token | Hex / value | Kotlin name | Usage |
| --- | --- | --- | --- |
| `color.bg.app` | `#020617` | `TonezenAppBg` | App background, text on teal controls |
| `color.bg.screenGradientTop` | `#070B0F` | — | Auth screen gradient top |
| `color.bg.screenGradientBottom` | `#071016` | — | Auth screen gradient bottom |
| `color.bg.surface` | `#0f172a` | `TonezenSurface` | Scaffold, library scroll surface |
| `color.bg.surfaceRaised` | `#172033` | `TonezenSurfaceRaised` | Cards, search fields, chrome fill |
| `color.bg.surfaceMuted` | `#1e293b` | `TonezenSurfaceMuted` | Inactive controls |
| `color.bg.sheet` | `#162032` | `TonezenSheetBg` | Sheet base (via glass overlay) |
| `color.bg.chrome` | `#172033` @ 62% | `TonezenChromeBarBackground` | Frosted top/bottom chrome |
| `color.text.primary` | `#f8fafc` | `TonezenInk` | Main text |
| `color.text.secondary` | `#94a3b8` | `TonezenMuted` | Metadata, inactive labels |
| `color.text.muted` | `#64748b` | `TonezenFaint` | Helper text, member-since |
| `color.accent.primary` | `#5eead4` | `TonezenTeal` | Selected tabs, links, progress, nav |
| `color.accent.primaryStrong` | `#14b8a6` | `TonezenTealStrong` | Gradient end, filled buttons |
| `color.accent.progress` | `#fcd34d` | `TonezenAmber` | Active row tint, playing bars, offline banner |
| `color.accent.coverTitle` | `#ffe7ba` | — | Uppercase cycle cover titles |
| `color.status.online` | `#4ade80` | `TonezenGreen` | Online chip in profile |
| `color.status.offline` | `#fcd34d` | `TonezenAmber` | Offline chip, pending sync |
| `color.status.synced` | `#4ade80` | `TonezenGreen` | Successful sync (when shown) |
| `color.status.downloaded` | `#5eead4` | `TonezenTeal` | Downloaded indicator, offline availability chip |
| `color.status.error` | `#f87171` | `TonezenError` | Errors, destructive actions |
| `color.border.default` | `#334155` | `TonezenBorder` | Card and row borders |
| `color.border.chrome` | `#ffffff` @ 12% | `TonezenChromeBarBorder` | Glass chrome outline |
| `color.border.subtle` | `#ffffff` @ 10–13% | — | Cover art, search field borders |
| `color.control.frosted` | `#ffffff` @ 6–8% | — | Round secondary controls |

### Playback Button Gradients

Main and compact play buttons share the same logic (`PlayButton`, `CompactMediaPlayButton`):

| State | Gradient stops |
| --- | --- |
| Idle / paused | `#5eead4` → `#14b8a6` → `#0d9488` |
| Playing | `#14b8a6` → `#0d9488` → `#0f766e` |

Circle border: white @ 22%. Icon tint: `TonezenAppBg`.

Auth sign-in button: vertical `#5eead4` → `#14b8a6`, height 54 dp, radius 17 dp.

## Typography

Android uses **Material 3 default typography** (`MaterialTheme.typography`) with no custom type scale. Do not add viewport-scaled type. If text does not fit, wrap first; reduce density before going below `labelSmall`.

| M3 style | Size / line | Weight in app | Usage |
| --- | ---: | --- | --- |
| `headlineLarge` | 32 / 40 | Bold | Auth app name |
| `headlineSmall` | 24 / 32 | Bold | Auth headline, now playing title |
| `titleLarge` | 22 / 28 | Bold | Cycle cover title, empty-library heading |
| `titleMedium` | 16 / 24 | SemiBold | Screen chrome titles, profile, book detail |
| `titleSmall` | 14 / 20 | SemiBold when selected | Library tabs |
| `bodyLarge` | 16 / 24 | SemiBold on profile name | Auth body, profile card |
| `bodyMedium` | 14 / 20 | Regular | Row titles, offline banner |
| `bodySmall` | 12 / 16 | Regular | Metadata, durations, errors |
| `labelLarge` | 14 / 20 | Bold | Mini-player cover initials |
| `labelMedium` | 12 / 16 | SemiBold | Cycle progress overlay |
| `labelSmall` | 11 / 16 | Medium / SemiBold | Chips, bottom nav, round controls |

Font family: platform default sans (Roboto on Android). Desktop may use Inter or system UI sans with the same sizes.

## Spacing And Shape

Values from `TonezenLayout.kt` and shared components.

| Token | Value | Usage |
| --- | ---: | --- |
| `spacing.4` | 4 dp | Nav icon-to-label gap |
| `spacing.8` | 8 dp | Chips, compact rows, chrome outer margin |
| `spacing.10` | 10 dp | Card overlay insets, filter chip padding |
| `spacing.12` | 12 dp | Row internals, tab underline gap |
| `spacing.14` | 14 dp | Search field horizontal padding |
| `spacing.16` | 16 dp | Section rhythm, card padding, overlay scroll gap |
| `spacing.20` | 20 dp | Screen horizontal padding, sheet content |
| `spacing.24` | 24 dp | Large vertical separation, auth horizontal padding |
| `spacing.32` | 32 dp | Major screen separation |
| `radius.6` | 6 dp | Bottom nav icon box |
| `radius.8` | 8 dp | Mini-player cover |
| `radius.10` | 10 dp | Active track/chapter row |
| `radius.12` | 12 dp | Search, filter chips, action buttons, offline banner |
| `radius.14` | 14 dp | Cycle and book cover cards |
| `radius.16` | 16 dp | Profile cards, glass chrome bar |
| `radius.17` | 17 dp | Auth sign-in button |
| `radius.24` | 24 dp | Bottom sheet top corners, large cover art |
| `radius.pill` | 999 dp | Status chips, progress track |

### Chrome Dimensions

| Element | Height / size |
| --- | ---: |
| Bottom nav content | 58 dp |
| Mini-player body | 75 dp |
| Mini-player progress strip | 3 dp |
| Main progress bar track | 4 dp (28 dp touch target when seekable) |
| Top chrome header row | 40 dp min |
| Compact play button | 36 dp |
| Main play button (now playing) | 64 dp |
| Mini-player play button | 40 dp |
| Search / filter control | 48 dp |

## Navigation Model

The shell (`AppShell.kt`) uses **two bottom destinations**:

| Tab | Label (`strings.xml`) | Role |
| --- | --- | --- |
| Library | `nav_library` → «Библиотека» | Audiobook cycles, music tracks, overlays |
| Profile | `nav_profile` → «Профиль» | Account, sync, storage, sign-out |

There is **no separate Player or Downloads tab**. Playback expands from the mini-player into a bottom sheet. Download management lives in library actions and **Profile → Storage**.

Mini-player sits above bottom navigation when playback is active. Bottom chrome hides on library overlays (cycle detail, book detail).

## Screen Inventory

### Sign In

`AuthScreen.kt`, `AuthDecor.kt`

- Full-screen gradient background with subtle star field (teal/amber glows, not decorative blobs).
- Hero: app name, headline «Ваша библиотека, всегда офлайн», body about offline playback and audiobook sync.
- Value pills: «Офлайн-воспроизведение» (teal), «Синхронизация прогресса» (amber).
- Stacked cover preview cards as visual anchor.
- Sign-in fields in Material outlined inputs; primary button is teal gradient.
- Footer note: expired session stays active offline.

### Library

`LibraryScreen.kt`

- Frosted top chrome with tabs **«Аудиокниги»** / **«Музыка»** (teal underline on selected).
- Audiobooks tab: search row + filter (audiobooks only); filter opens glass bottom sheet.
- Audiobooks content: **2-column grid of cycle cover cards**, not single-book shelves.
- Each cycle card: gradient cover, uppercase title, book count, optional listen % overlay, teal check when downloaded, compact teal play button.
- Tap card → cycle detail; tap play → cycle playback.
- Music tab: «download all» action when applicable, then **track list rows** (not cover grid).
- Offline banner (amber) when network unavailable.
- Mini-player pinned above bottom nav during playback.

### Cycle Detail

`CycleDetailScreen.kt`

- Back chrome with title «Книги цикла» and overflow menu (download, listened, remove downloads).
- Vertical list of books in the cycle with cover thumbnails and offline chip.

### Book Detail (Chapters)

`BookDetailScreen.kt`

- Back chrome titled «Главы» with overflow menu for book-level actions.
- Chapter list rows: active chapter uses amber row tint and `PlayingBars`; progress sub-bar when partially listened.
- Download confirmation opens glass bottom sheet.

### Now Playing

`NowPlayingScreen.kt` — **modal bottom sheet**, not a root tab.

- Large cover art (168 dp, 24 dp radius) with optional download progress ring.
- Title + teal subtitle (artist/chapter).
- Teal seekable progress bar with ±15 s round controls and skip previous/next.
- 64 dp teal gradient play/pause button.

### Profile

`ProfileScreen.kt`

- Title chrome «Профиль» with online (green) or offline (amber) status chip.
- User card (tap → Account settings): avatar, name, email, member since.
- Sync status card with last sync time and pending count (amber «Ожидает» when applicable).
- Settings group: **Storage** only on main screen; Account reached via user card.
- Sign-out row at bottom.

### Account Settings

`AccountSettingsScreen.kt`

- Profile fields, avatar crop flow, password change.
- Requires network for online-only actions.

### Storage Settings

`StorageSettingsScreen.kt`

- Downloaded bytes summary and «Удалить всё» destructive action.
- Replaces a standalone Downloads tab; no per-file list screen yet.

## Modal And Sheet Patterns

All overlays use `TonezenGlassModalBottomSheet` or `TonezenGlassAlertDialog` (`TonezenSheets.kt`): dimmed backdrop, 24 dp top radius, drag handle, frosted surface.

### Library Filter Sheet

- Content filters: all / downloaded.
- Sort: recently played / title.
- Reset (outlined) + Apply (teal filled).

### Download Confirmation Sheet

- Storage estimate before download.
- Primary: «Скачать офлайн»; secondary: cancel.

### Now Playing Sheet

- Full player layout inside glass bottom sheet.

### Offline Sync Dialog

- Title «Синхронизация приостановлена»; chips Offline / Pending.
- Actions: retry sync, dismiss.

### Sign Out / Delete All Confirm

- Alert dialog on glass surface; destructive label in error red.

## Components

### Cycle Cover Card

`CycleCover.kt`, `LibraryCycleCard`

Gradient cover (seed from cycle id), 14 dp radius, white 13% border. Title in `#ffe7ba` uppercase. Book count below. Optional teal check, progress %, compact play button overlays.

### Book Cover

`TonezenBookCover.kt` — same shell for single-book thumbnails in cycle detail.

### Track / Chapter Row

`TonezenTrackListRow.kt`

Rounded 10 dp row. Active state: amber 8% fill, amber 18% border, teal or amber subtitle. Optional listen-progress bar. Trailing play or downloaded indicator.

### Music Track Row

`MusicTrackList.kt` — list row with inline progress for active track.

### Status Chip

`StatusChip` in `TonezenChips.kt`

Pill with 6 dp dot + label. Background tone @ 17%, border @ 28%.

| Label | Tone | When |
| --- | --- | --- |
| «Онлайн» | Green | Profile, authenticated online |
| «Офлайн» | Amber | Profile offline, or network banner context |
| «Офлайн» (downloaded) | Teal | Downloaded content availability |
| «Ожидает» | Amber | Pending audiobook sync |

### Offline Banner

Amber tinted card with «Нет сети — синхронизация приостановлена».

### Search Row

48 dp raised field (12 dp radius) + separate 48 dp filter button. Audiobooks tab only.

### Segmented Tabs

`TonezenTabs` — full-width labels with 2 dp bottom indicator; selected teal, inactive muted.

### Mini Player

Cover 48 dp, title/subtitle, 40 dp play button, 3 dp teal progress strip above bottom nav.

### Primary Button

Teal filled (`ButtonDefaults` with `TonezenTeal` / `TonezenAppBg`) for form actions. Teal gradient circles for playback.

### Secondary Control

`RoundControl` / `RoundIconControl` — frosted circle, optional white 16% outline, 40–48 dp.

### Bottom Navigation

Two items with 26 dp rounded icon box: selected teal fill + teal border; inactive muted outline. Label `labelSmall`.

### Glass Chrome Bar

`TonezenChrome.kt` — 16 dp corner radius, haze blur 24 dp, noise 0.08, divider below top chrome.

## Content Rules

- Top-level content groups: **«Аудиокниги»** and **«Музыка»** (`tab_audiobooks`, `tab_music`).
- Audiobook library is organized by **cycles**, not flat book shelves.
- Use «Офлайн» for downloaded/playable-local content; teal when marking availability on items.
- Use sync labels only for audiobook server state.
- Do not show server-sync progress for music.
- Music progress copy must say local/on-device when mentioned.
- All user-facing strings are **Russian** in `strings.xml`; no English in production UI.
- Error and offline messages should be short and actionable.

## Implementation Notes

- Android Compose: brand tokens in `ui/theme/`, strings in `strings.xml`, components in `ui/components/`.
- Material `colorScheme` maps teal → `primary`, amber → `secondary`, `surfaceTint = Transparent`.
- Scroll surfaces register with `HazeState` so chrome and sheets blur content behind them.
- Generated cover gradients (`coverBrush`, `trackCoverBrush`) are dev/catalog fallbacks; production art from signed URLs.
- Desktop maps the same token names to Tailwind/CSS variables.
- Avoid direct HTTP access to content volume; use signed URLs for remote media and artwork.
