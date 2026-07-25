export const queryKeys = {
  libraryBundle: (reconcileLocalPaths: boolean) =>
    ["libraryBundle", { reconcileLocalPaths }] as const,
  libraryBundleAll: ["libraryBundle"] as const,
  progress: ["progress"] as const,
  storageStats: ["storageStats"] as const,
  syncStatus: ["syncStatus"] as const,
  tracks: (bookId: string) => ["tracks", bookId] as const,
} as const;
