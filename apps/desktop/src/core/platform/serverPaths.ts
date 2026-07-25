export const API_V1_PREFIX = "/api/v1";

export function apiV1Url(baseUrl: string, path: string): string {
  const root = baseUrl.replace(/\/$/, "");
  const suffix = path.startsWith("/") ? path : `/${path}`;
  return `${root}${API_V1_PREFIX}${suffix}`;
}
