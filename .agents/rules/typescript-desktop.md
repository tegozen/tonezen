# Desktop (Electron)

Apply under `apps/desktop/**`.

- Use electron-vite, React, and strict TypeScript.
- Aliases: `@core/*` maps to `src/core/*`; `@/*` maps to `src/renderer/src/*`.
- Cross-process domain belongs in `src/core/`: auth, catalog, downloads, playback, profile, progress, platform, and IPC.
- Split the main process by domain under `main/{app,window,media,catalog,downloads,progress,profile,session,db,ipc}/`.
- Renderer follows Feature-Sliced Design: `app/ pages/ widgets/ features/ entities/ shared/`, with slice `index.ts` public APIs.
- Enforce FSD with Steiger through `npm run lint:fsd`.
- Close/minimize hides to tray; `app.quit()` is allowed only from tray «Exit».
- Use `WindowLifecycleManager` with an `isQuitting` flag.
- Supabase JS authentication must remain offline-safe.
- User-facing copy is inline Russian text; do not introduce a central i18n module.
- Keep modules around 200 lines or less; split god files.
- Tailwind v4 enters through `app/styles/tailwind.css`. Prefer `*.module.css` with `@reference "../…/tailwind.css"`; keep legacy globals in `app/styles/styles.css`. Do not use Sass/SCSS.
