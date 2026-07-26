export interface ProgressSyncConfig {
  baseUrl: string;
  anonKey: string;
}

export type ProgressRow = {
  book_id: string;
  track_id: string;
  position_ms: number;
  updated_at: string;
  user_id?: string;
};

export type ProgressPushResponse = {
  skipped?: boolean;
  progress?: ProgressRow;
};
