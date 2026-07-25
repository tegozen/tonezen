export function isRefreshAuthFailure(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return /\((400|401|403)\)/.test(message);
}
