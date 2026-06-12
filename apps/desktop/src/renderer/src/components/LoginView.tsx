import { useState } from "react";
import {
  DownloadsIcon,
  LockIcon,
  MailIcon,
  SyncIcon,
} from "./TonezenIcons";
import { strings } from "../i18n/strings";

interface LoginViewProps {
  email: string;
  password: string;
  error: string | null;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onLogin: () => void;
}

export function LoginView({
  email,
  password,
  error,
  onEmailChange,
  onPasswordChange,
  onLogin,
}: LoginViewProps) {
  const [passwordVisible, setPasswordVisible] = useState(false);

  return (
    <div className="app-frame">
      <main className="app-content space-y-6">
        <div>
          <h1 className="text-3xl font-bold">{strings.appName}</h1>
          <h2 className="mt-4 text-2xl font-bold">{strings.authHeadline}</h2>
          <p className="mt-2 text-muted">{strings.authBody}</p>
        </div>
        <div className="flex gap-2">
          <span className="chip-teal gap-1">
            <DownloadsIcon className="h-3.5 w-3.5" />
            {strings.authOfflineBadge}
          </span>
          <span className="chip-green gap-1">
            <SyncIcon className="h-3.5 w-3.5" />
            {strings.authSyncBadge}
          </span>
        </div>
        <div className="grid grid-cols-3 gap-3">
          {["Midnight", "Atomic", "Body"].map((label) => (
            <div key={label} className="aspect-[0.78] rounded-2xl bg-surface-raised p-2 text-center text-xs">
              {label}
            </div>
          ))}
        </div>
        <div className="card space-y-3">
          <div>
            <h3 className="font-semibold">{strings.authCardTitle}</h3>
            <p className="text-sm text-muted">{strings.authCardBody}</p>
          </div>
          <div className="relative">
            <MailIcon className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-muted" />
            <input
              className="input-field pl-11"
              placeholder={strings.email}
              value={email}
              onChange={(e) => onEmailChange(e.target.value)}
            />
          </div>
          <div className="relative">
            <LockIcon className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-muted" />
            <input
              className="input-field pl-11 pr-16"
              placeholder={strings.password}
              type={passwordVisible ? "text" : "password"}
              value={password}
              onChange={(e) => onPasswordChange(e.target.value)}
            />
            <button
              type="button"
              className="absolute right-3 top-3 text-sm text-muted"
              onClick={() => setPasswordVisible((value) => !value)}
            >
              {passwordVisible ? strings.hidePassword : strings.showPassword}
            </button>
          </div>
          <button className="btn-primary w-full" type="button" onClick={onLogin}>
            {strings.signIn}
          </button>
          {error && <p className="error-text">{error}</p>}
        </div>
        <p className="text-center text-sm text-muted">{strings.offlinePlaybackNote}</p>
      </main>
    </div>
  );
}
