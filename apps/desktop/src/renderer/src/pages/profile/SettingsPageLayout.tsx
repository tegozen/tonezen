import type { ReactNode } from "react";
import { ChevronLeftIcon } from "../../components/TonezenIcons";
import { strings } from "../../i18n/strings";

interface SettingsPageLayoutProps {
  title: string;
  onBack: () => void;
  children: ReactNode;
}

export function SettingsPageLayout({ title, onBack, children }: SettingsPageLayoutProps) {
  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3">
        <button type="button" className="icon-button h-10 w-10" onClick={onBack} aria-label={strings.back}>
          <ChevronLeftIcon className="h-5 w-5" />
        </button>
        <h1 className="text-2xl font-bold">{title}</h1>
      </div>
      {children}
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
