# Android Navigation Compose

Use this checklist for Android navigation work in Tonezen.

- Use Navigation Compose with `@Serializable` type-safe routes; do not introduce manual screen switching through large `when` blocks.
- Route arguments contain only stable IDs and simple serializable values. Resolve `Book`, `Cycle`, and other models from current repository or ViewModel state at the destination.
- The navigation back stack is the single source of truth for the current page. Do not mirror the active route, selected model, or back stack in `UiState` or a ViewModel.
- Screens expose navigation callbacks such as `onBack` and `onBookClick`; they do not receive or call a `NavController`. Graph and route hosts own `navigate()` and `popBackStack()`.
- Scope destination-specific ViewModels to their `NavBackStackEntry`; keep app-shell playback and chrome state in the shell ViewModel.
- Bottom navigation uses `launchSingleTop = true`, `restoreState = true`, and `popUpTo(...) { saveState = true }` so every tab preserves its back stack and scroll position.
- Full-screen pages are destinations. Dialogs, bottom sheets, avatar crop, and expanded-player UI remain transient state overlays.
- System Back dismisses the topmost transient overlay first, then delegates to `popBackStack()`. Declare overlay `BackHandler` instances after the `NavHost` so they have priority.
- Handle restored routes whose entity no longer exists by returning to that feature's root destination; never leave an empty or crashing detail screen.
- Consume one-shot route actions, such as auto-resume, once with saveable destination state so recomposition or restoration cannot repeat them.
