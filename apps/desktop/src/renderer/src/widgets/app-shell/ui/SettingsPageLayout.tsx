import type { ReactNode } from "react";
import { OVERLAY_BACK_TOP_SCROLL_PX } from "@/shared/lib/layoutChrome";

interface SettingsPageLayoutProps {
  topChrome: ReactNode;
  children: ReactNode;
}

export function SettingsPageLayout({ topChrome, children }: SettingsPageLayoutProps) {
  return (
    <div className="profile-page">
      <div className="scroll-under-chrome space-y-4" style={{ paddingTop: OVERLAY_BACK_TOP_SCROLL_PX }}>
        {children}
      </div>
      {topChrome}
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
