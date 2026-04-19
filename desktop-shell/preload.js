const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("desktopShell", {
  showContextMenu: () => ipcRenderer.invoke("shell:show-context-menu"),
  startDrag: (screenX, screenY) => ipcRenderer.send("shell:drag-start", screenX, screenY),
  dragMove: (screenX, screenY) => ipcRenderer.send("shell:drag-move", screenX, screenY),
  endDrag: () => ipcRenderer.send("shell:drag-end")
});
