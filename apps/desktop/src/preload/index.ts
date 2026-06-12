import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("tonezen", {
  session: {
    get: () => ipcRenderer.invoke("session:get"),
    setOnline: (online: boolean) => ipcRenderer.invoke("session:setOnline", online),
    login: (email: string, password: string) => ipcRenderer.invoke("session:login", email, password),
    logout: () => ipcRenderer.invoke("session:logout"),
  },
  catalog: {
    sync: () => ipcRenderer.invoke("catalog:sync"),
  },
  db: {
    getBooks: () => ipcRenderer.invoke("db:getBooks"),
    getTracks: (bookId: string) => ipcRenderer.invoke("db:getTracks", bookId),
  },
  download: {
    track: (bookId: string, trackId: string) => ipcRenderer.invoke("download:track", bookId, trackId),
    delete: (bookId: string, trackId: string) => ipcRenderer.invoke("download:delete", bookId, trackId),
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
