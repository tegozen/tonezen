import { BOTTOM_NAV_TABS } from "@shared/navigation";
import type { BottomTab } from "../i18n/strings";
import { strings } from "../i18n/strings";
import { BooksIcon, DownloadsIcon, MusicIcon, ProfileIcon } from "./TonezenIcons";

interface BottomNavProps {
  active: BottomTab;
  onSelect: (tab: BottomTab) => void;
}

const tabs: Array<{
  id: BottomTab;
  label: string;
  Icon: typeof MusicIcon;
}> = BOTTOM_NAV_TABS.map((id) => {
  switch (id) {
    case "music":
      return { id, label: strings.navMusic, Icon: MusicIcon };
    case "books":
      return { id, label: strings.navBooks, Icon: BooksIcon };
    case "downloads":
      return { id, label: strings.navDownloads, Icon: DownloadsIcon };
    case "profile":
      return { id, label: strings.navProfile, Icon: ProfileIcon };
  }
});

export function BottomNav({ active, onSelect }: BottomNavProps) {
  return (
    <nav className="bottom-nav">
      {tabs.map((tab) => {
        const selected = active === tab.id;
        return (
          <button
            key={tab.id}
            type="button"
            className={`bottom-nav-item ${selected ? "bottom-nav-item-active" : "text-muted"}`}
            onClick={() => onSelect(tab.id)}
          >
            <span className={`bottom-nav-glyph ${selected ? "bottom-nav-glyph-active" : ""}`}>
              <tab.Icon className="h-5 w-5" />
            </span>
            <span>{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
