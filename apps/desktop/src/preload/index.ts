import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("tonezen", {
  session: {
    get: () => ipcRenderer.invoke("session:get"),
    setOnline: (online: boolean) => ipcRenderer.invoke("session:setOnline", online),
    login: (email: string, password: string) => ipcRenderer.invoke("session:login", email, password),
    logout: () => ipcRenderer.invoke("session:logout"),
    updateProfile: (displayName: string) => ipcRenderer.invoke("session:updateProfile", displayName),
    changePassword: (newPassword: string) => ipcRenderer.invoke("session:changePassword", newPassword),
    uploadAvatar: (jpegBytes: Uint8Array) =>
      ipcRenderer.invoke("session:uploadAvatar", Array.from(jpegBytes)),
    onProfileUpdated: (
      callback: (snap: {
        state: string;
        email: string | null;
        displayName: string | null;
        avatarUrl: string | null;
        memberSinceEpochMs: number | null;
      }) => void,
    ) => {
      const listener = (
        _event: Electron.IpcRendererEvent,
        snap: {
          state: string;
          email: string | null;
          displayName: string | null;
          avatarUrl: string | null;
          memberSinceEpochMs: number | null;
        },
      ) => callback(snap);
      ipcRenderer.on("session:profileUpdated", listener);
      return () => ipcRenderer.removeListener("session:profileUpdated", listener);
    },
  },
  catalog: {
    sync: () => ipcRenderer.invoke("catalog:sync"),
    onUpdated: (callback: () => void) => {
      const listener = () => callback();
      ipcRenderer.on("catalog:updated", listener);
      return () => ipcRenderer.removeListener("catalog:updated", listener);
    },
  },
  db: {
    getBooks: () => ipcRenderer.invoke("db:getBooks"),
    getCycles: () => ipcRenderer.invoke("db:getCycles"),
    getLibrarySnapshot: () => ipcRenderer.invoke("db:getLibrarySnapshot"),
    getAllTracks: () => ipcRenderer.invoke("db:getAllTracks"),
    getAllProgress: () => ipcRenderer.invoke("db:getAllProgress"),
    getTracks: (bookId: string) => ipcRenderer.invoke("db:getTracks", bookId),
  },
  download: {
    track: (bookId: string, trackId: string) => ipcRenderer.invoke("download:track", bookId, trackId),
    delete: (bookId: string, trackId: string) => ipcRenderer.invoke("download:delete", bookId, trackId),
    list: () => ipcRenderer.invoke("download:list"),
    storageStats: () => ipcRenderer.invoke("download:storageStats"),
    deleteAll: () => ipcRenderer.invoke("download:deleteAll"),
    enqueue: (request: {
      bookId: string;
      trackId: string;
      priority: "PREFETCH" | "BULK" | "USER" | "PLAY";
      batchId?: string | null;
      title: string;
      subtitle?: string | null;
      contentType: string;
      enqueuedAt?: number;
    }) => ipcRenderer.invoke("download:enqueue", request),
    enqueueBatch: (
      requests: Array<{
        bookId: string;
        trackId: string;
        priority: "PREFETCH" | "BULK" | "USER" | "PLAY";
        batchId?: string | null;
        title: string;
        subtitle?: string | null;
        contentType: string;
        enqueuedAt?: number;
      }>,
      batchId?: string,
    ) => ipcRenderer.invoke("download:enqueueBatch", requests, batchId),
    awaitTrack: (
      bookId: string,
      trackId: string,
      options?: {
        priority?: "PREFETCH" | "BULK" | "USER" | "PLAY";
        title?: string;
        subtitle?: string | null;
        contentType?: string;
      },
    ) => ipcRenderer.invoke("download:awaitTrack", bookId, trackId, options),
    cancelTrack: (bookId: string, trackId: string) =>
      ipcRenderer.invoke("download:cancelTrack", bookId, trackId),
    cancelBatch: (batchId: string) => ipcRenderer.invoke("download:cancelBatch", batchId),
    cancelAll: () => ipcRenderer.invoke("download:cancelAll"),
    getQueueState: () => ipcRenderer.invoke("download:getQueueState"),
    onQueueState: (
      callback: (state: {
        queuedItems: Array<{
          bookId: string;
          trackId: string;
          title: string;
          subtitle: string | null;
          contentType: string;
          status: string;
          progress: number | null;
          batchId: string | null;
          enqueuedAt: number;
          completedAt: number | null;
        }>;
        completedHistory: Array<{
          bookId: string;
          trackId: string;
          title: string;
          subtitle: string | null;
          contentType: string;
          status: string;
          progress: number | null;
          batchId: string | null;
          enqueuedAt: number;
          completedAt: number | null;
        }>;
        activeBookId: string | null;
        activeTrackId: string | null;
        trackProgress: number | null;
        bulkDownloaded: number;
        bulkTotal: number;
        activeBatchId: string | null;
        pausedForNetwork: boolean;
      }) => void,
    ) => {
      const listener = (
        _event: Electron.IpcRendererEvent,
        state: Parameters<typeof callback>[0],
      ) => callback(state);
      ipcRenderer.on("download:queueState", listener);
      return () => ipcRenderer.removeListener("download:queueState", listener);
    },
  },
  sync: {
    status: () => ipcRenderer.invoke("sync:status"),
    trigger: () => ipcRenderer.invoke("sync:trigger"),
  },
  progress: {
    get: (bookId: string) => ipcRenderer.invoke("progress:get", bookId),
    save: (bookId: string, trackId: string, positionMs: number) =>
      ipcRenderer.invoke("progress:save", bookId, trackId, positionMs),
    onUpdated: (callback: (progress: unknown) => void) => {
      const listener = (_event: Electron.IpcRendererEvent, progress: unknown) => callback(progress);
      ipcRenderer.on("progress:updated", listener);
      return () => ipcRenderer.removeListener("progress:updated", listener);
    },
  },
  playback: {
    setActive: (active: boolean) => ipcRenderer.invoke("playback:setActive", active),
  },
});
