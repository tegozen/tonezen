import type { ReactNode } from "react";
import { BottomNav } from "./BottomNav";
import { MiniPlayerBar } from "./MiniPlayerBar";
import type { BottomTab } from "../i18n/strings";

interface AppShellProps {
  activeTab: BottomTab;
  onTabSelect: (tab: BottomTab) => void;
  miniTitle: string | null;
  miniSubtitle: string | null;
  coverSeed?: string;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
  showMiniPlayer: boolean;
  showBottomNav: boolean;
  miniDownloadProgress?: number | null;
  onMiniBarClick: () => void;
  onMiniPlayPause: () => void;
  children: ReactNode;
}

export function AppShell({
  activeTab,
  onTabSelect,
  miniTitle,
  miniSubtitle,
  coverSeed,
  isPlaying,
  positionMs,
  durationMs,
  showMiniPlayer,
  showBottomNav,
  miniDownloadProgress = null,
  onMiniBarClick,
  onMiniPlayPause,
  children,
}: AppShellProps) {
  const progress = durationMs > 0 ? positionMs / durationMs : 0;
  const showBottomChrome = showMiniPlayer || showBottomNav;

  return (
    <div className="app-frame">
      <main
        className={`app-content ${showBottomChrome ? (showMiniPlayer ? "app-content-with-mini" : "app-content-with-nav") : ""} ${!showBottomNav ? "app-content-overlay" : ""}`}
      >
        {children}
      </main>
      {showBottomChrome && (
        <div className="bottom-chrome-wrap">
          <div className="bottom-chrome-shell">
            {showMiniPlayer && (
              <MiniPlayerBar
                title={miniTitle}
                subtitle={miniSubtitle}
                coverSeed={coverSeed}
                isPlaying={isPlaying}
                progress={progress}
                downloadProgress={miniDownloadProgress}
                onBarClick={onMiniBarClick}
                onPlayPause={onMiniPlayPause}
              />
            )}
            {showMiniPlayer && showBottomNav && <div className="bottom-chrome-divider" />}
            {showBottomNav && <BottomNav active={activeTab} onSelect={onTabSelect} />}
          </div>
        </div>
      )}
    </div>
  );
}
