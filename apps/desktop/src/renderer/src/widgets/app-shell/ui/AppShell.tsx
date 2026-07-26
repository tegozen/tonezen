import type { ReactNode, CSSProperties } from "react";
import { scrollPadBottomCss } from "@/shared/lib/layoutChrome";
import styles from "./AppShell.module.scss";

interface AppShellProps {
  showMiniPlayer: boolean;
  showBottomNav: boolean;
  miniPlayer?: ReactNode;
  bottomNav?: ReactNode;
  children: ReactNode;
}

export function AppShell({
  showMiniPlayer,
  showBottomNav,
  miniPlayer,
  bottomNav,
  children,
}: AppShellProps) {
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
            {showMiniPlayer ? miniPlayer : null}
            {showMiniPlayer && showBottomNav && <div className="bottom-chrome-divider" />}
            {showBottomNav ? bottomNav : null}
          </div>
        </div>
      )}
    </div>
  );
}
