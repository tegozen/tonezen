interface AppShellErrorBannerProps {
  error: string | null;
}

export function AppShellErrorBanner({ error }: AppShellErrorBannerProps) {
  if (!error) return null;
  return <p className="error-text">{error}</p>;
}
