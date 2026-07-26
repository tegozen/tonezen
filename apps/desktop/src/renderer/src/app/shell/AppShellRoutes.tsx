import { AppShellBookRoute } from "./AppShellBookRoute";
import { AppShellCycleRoute } from "./AppShellCycleRoute";
import { AppShellDownloadsRoute } from "./AppShellDownloadsRoute";
import { AppShellLibraryRoute } from "./AppShellLibraryRoute";
import { AppShellProfileRoute } from "./AppShellProfileRoute";
import type { AppShellRoutesProps } from "../model";

export type { AppShellRoutesProps } from "../model";

export function AppShellRoutes(props: AppShellRoutesProps) {
  const { library, activeTab } = props;

  if (library.selectedBook) {
    return <AppShellBookRoute {...props} />;
  }

  if (library.selectedCycle) {
    return <AppShellCycleRoute {...props} />;
  }

  if (activeTab === "music" || activeTab === "books") {
    return <AppShellLibraryRoute {...props} activeTab={activeTab} />;
  }

  if (activeTab === "downloads") {
    return <AppShellDownloadsRoute {...props} />;
  }

  return <AppShellProfileRoute {...props} />;
}
