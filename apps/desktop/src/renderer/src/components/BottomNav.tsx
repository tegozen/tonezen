import type { BottomTab } from "../i18n/strings";
import { strings } from "../i18n/strings";

interface BottomNavProps {
  active: BottomTab;
  onSelect: (tab: BottomTab) => void;
}

const tabs: Array<{ id: BottomTab; label: string }> = [
  { id: "library", label: strings.navLibrary },
  { id: "player", label: strings.navPlayer },
  { id: "downloads", label: strings.navDownloads },
  { id: "profile", label: strings.navProfile },
];

export function BottomNav({ active, onSelect }: BottomNavProps) {
  return (
    <nav className="bottom-nav">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          className={`bottom-nav-item ${active === tab.id ? "bottom-nav-item-active" : "text-muted"}`}
          onClick={() => onSelect(tab.id)}
        >
          <span>{tab.label.slice(0, 1)}</span>
          <span>{tab.label}</span>
        </button>
      ))}
    </nav>
  );
}
