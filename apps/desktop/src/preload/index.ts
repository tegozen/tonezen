import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("tplayer", {
  session: {
    get: () => ipcRenderer.invoke("session:get"),
    login: (email: string, password: string) => ipcRenderer.invoke("session:login", email, password),
    logout: () => ipcRenderer.invoke("session:logout"),
    refreshIfNeeded: () => ipcRenderer.invoke("session:refreshIfNeeded"),
  },
  db: {
    getBooks: () => ipcRenderer.invoke("db:getBooks"),
    getTracks: (bookId: string) => ipcRenderer.invoke("db:getTracks", bookId),
  },
  playback: {
    setActive: (active: boolean) => ipcRenderer.invoke("playback:setActive", active),
  },
});
