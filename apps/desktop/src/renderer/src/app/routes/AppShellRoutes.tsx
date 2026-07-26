import { AppShellBookRoute } from "@/app/AppShellBookRoute";
import { AppShellCycleRoute } from "@/app/AppShellCycleRoute";
import { AppShellDownloadsRoute } from "@/app/AppShellDownloadsRoute";
import { AppShellLibraryRoute } from "@/app/AppShellLibraryRoute";
import { AppShellProfileRoute } from "@/app/AppShellProfileRoute";
import type { AppShellRoutesProps } from "@/app/appShellRoutesProps";

export type { AppShellRoutesProps } from "@/app/appShellRoutesProps";

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
