import type { ReactNode } from "react";
import { BottomNav } from "./BottomNav";
import { MiniPlayerBar } from "./MiniPlayerBar";
import type { BottomTab } from "../i18n/strings";

interface AppShellProps {
  activeTab: BottomTab;
  onTabSelect: (tab: BottomTab) => void;
  miniTitle: string | null;
  miniSubtitle: string | null;
  isPlaying: boolean;
  onMiniBarClick: () => void;
  onMiniPlayPause: () => void;
  children: ReactNode;
}

export function AppShell({
  activeTab,
  onTabSelect,
  miniTitle,
  miniSubtitle,
  isPlaying,
  onMiniBarClick,
  onMiniPlayPause,
  children,
}: AppShellProps) {
  return (
    <div className="app-frame">
      <main className="app-content">{children}</main>
      <MiniPlayerBar
        title={miniTitle}
        subtitle={miniSubtitle}
        isPlaying={isPlaying}
        onBarClick={onMiniBarClick}
        onPlayPause={onMiniPlayPause}
      />
      <BottomNav active={activeTab} onSelect={onTabSelect} />
    </div>
  );
}
