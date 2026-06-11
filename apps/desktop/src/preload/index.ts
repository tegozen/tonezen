import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("tplayer", {
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
  playback: {
    setActive: (active: boolean) => ipcRenderer.invoke("playback:setActive", active),
  },
});
