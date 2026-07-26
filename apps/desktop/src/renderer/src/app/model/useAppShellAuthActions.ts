import { useCallback } from "react";
import type { Book, Cycle } from "@core/types";
import { getTonezenApi } from "@/shared/api";
import type { useLibraryController } from "@/features/library";
import type { useMusicPlayback } from "@/features/music-queue";

type Library = ReturnType<typeof useLibraryController>;
type Music = ReturnType<typeof useMusicPlayback>;

interface UseAppShellAuthActionsOptions {
  login: () => Promise<boolean>;
  logout: () => Promise<void>;
  library: Library;
  music: Music;
  stopPlayback: () => void;
}

export function useAppShellAuthActions({
  login,
  logout,
  library,
  music,
  stopPlayback,
}: UseAppShellAuthActionsOptions) {
  const syncCatalog = useCallback(async () => {
    try {
      await getTonezenApi().catalog.sync();
      await library.refreshLibrary({ rebuildMusic: true });
    } catch {
      await library.refreshLibrary({ rebuildMusic: true });
    }
  }, [library]);

  const handleLogin = useCallback(async () => {
    const ok = await login();
    if (ok) {
      await syncCatalog();
    }
  }, [login, syncCatalog]);

  const handleLogout = useCallback(async () => {
    stopPlayback();
    library.setSelectedBook(null);
    library.setSelectedCycle(null);
    music.resetMusicSession();
    library.musicStartedInSessionRef.current = false;
    await logout();
  }, [library, logout, music, stopPlayback]);

  const openBook = useCallback(
    (book: Book, fromCycle: Cycle | null = library.selectedCycle) => {
      music.setMusicMode(false);
      return library.openBook(book, fromCycle);
    },
    [library, music],
  );

  return { handleLogin, handleLogout, openBook };
}
