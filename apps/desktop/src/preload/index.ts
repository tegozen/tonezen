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
