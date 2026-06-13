import type { BottomTab } from "../i18n/strings";
import { strings } from "../i18n/strings";
import { LibraryIcon, ProfileIcon } from "./TonezenIcons";

interface BottomNavProps {
  active: BottomTab;
  onSelect: (tab: BottomTab) => void;
}

const tabs: Array<{
  id: BottomTab;
  label: string;
  Icon: typeof LibraryIcon;
}> = [
  { id: "library", label: strings.navLibrary, Icon: LibraryIcon },
  { id: "profile", label: strings.navProfile, Icon: ProfileIcon },
];

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
