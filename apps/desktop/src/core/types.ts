export type SessionState =
  | "AuthenticatedOnline"
  | "AuthenticatedOffline"
  | "AuthenticatedStale"
  | "Unauthenticated";

export interface StoredSession {
  userId: string;
  email: string;
  displayName: string;
  accessToken: string;
  refreshToken: string;
  expiresAtEpochSeconds: number;
  memberSinceEpochMs?: number | null;
  avatarUrl?: string | null;
  profileUpdatedAt?: string | null;
}

export interface AudiobookProgress {
  bookId: string;
  trackId: string;
  positionMs: number;
  updatedAt: string;
}

export type ContentType = "audiobook" | "music";

export interface Book {
  id: string;
  slug: string;
  contentType: ContentType;
  title: string;
  author?: string;
}

export interface Track {
  id: string;
  bookId: string;
  sortOrder: number;
  title: string;
  filename: string;
  artist?: string;
  durationMs?: number;
  localPath?: string;
  localDownloadedAt?: number;
  waveformPeaks?: number[];
}

export interface Cycle {
  id: string;
  slug: string;
  title: string;
  bookOrder: string[];
  books: Book[];
}
