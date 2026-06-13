import type { ReactNode } from "react";
import { OVERLAY_BACK_TOP_SCROLL_PX } from "../../lib/layoutChrome";
import { OverlayTopChrome } from "../../components/OverlayTopChrome";

interface SettingsPageLayoutProps {
  title: string;
  onBack: () => void;
  children: ReactNode;
}

export function SettingsPageLayout({ title, onBack, children }: SettingsPageLayoutProps) {
  return (
    <div className="profile-page">
      <div className="scroll-under-chrome space-y-4" style={{ paddingTop: OVERLAY_BACK_TOP_SCROLL_PX }}>
        {children}
      </div>
      <OverlayTopChrome title={title} onBack={onBack} />
    </div>
  );
}

export function SettingsSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="card space-y-3">
      <h2 className="font-semibold">{title}</h2>
      {children}
    </div>
  );
}

export function SettingsInfoRow({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div>
      <div className="font-medium">{title}</div>
      <div className="text-sm text-muted">{subtitle}</div>
    </div>
  );
}
