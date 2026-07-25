import type { ReactNode, CSSProperties } from "react";
import { BottomNav } from "./BottomNav";
import { MiniPlayerBar } from "@/widgets/mini-player";
import type { BottomTab } from "@core/platform/navigation";
import { scrollPadBottomCss } from "@/shared/lib/layoutChrome";
import styles from "./AppShell.module.scss";

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
  const contentStyle = {
    "--scroll-pad-bottom": scrollPadBottomCss(showMiniPlayer, showBottomNav),
  } as CSSProperties;

  return (
    <div className={styles.frame}>
      <main
        className={`${styles.content} ${!showBottomNav ? styles.contentOverlay : ""}`}
        style={contentStyle}
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
