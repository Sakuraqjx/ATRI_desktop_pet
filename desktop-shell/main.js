const { app, BrowserWindow, Menu, ipcMain } = require("electron");
const path = require("path");

let win = null;
let dragOffset = null;

const backendUrl = (() => {
  const value = process.argv.find((arg) => arg.startsWith("--backend="));
  return value ? value.slice("--backend=".length) : "http://127.0.0.1:18080";
})();

function createWindow() {
  win = new BrowserWindow({
    width: 430,
    height: 560,
    frame: false,
    transparent: true,
    resizable: false,
    hasShadow: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    backgroundColor: "#00000000",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      sandbox: false,
      backgroundThrottling: false
    }
  });

  win.setAlwaysOnTop(true, "screen-saver");
  win.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  win.loadURL(`${backendUrl}/index.html`);

  win.on("closed", () => {
    fetch(`${backendUrl}/api/exit`, { method: "POST" }).catch(() => {});
    win = null;
  });
}

function nestCommands(commands) {
  const root = new Map();

  for (const command of commands) {
    const parts = command.label.split("/");
    let cursor = root;

    for (let index = 0; index < parts.length; index += 1) {
      const part = parts[index];
      const isLeaf = index === parts.length - 1;

      if (isLeaf) {
        cursor.set(part, command);
        continue;
      }

      if (!cursor.has(part) || !(cursor.get(part) instanceof Map)) {
        cursor.set(part, new Map());
      }
      cursor = cursor.get(part);
    }
  }

  return root;
}

function buildTemplate(tree) {
  const template = [];

  for (const [label, value] of tree.entries()) {
    if (value instanceof Map) {
      template.push({
        label,
        submenu: buildTemplate(value)
      });
      continue;
    }

    template.push({
      label,
      click: async () => {
        await fetch(
          `${backendUrl}/api/menu/execute?id=${encodeURIComponent(value.id)}`,
          { method: "POST" }
        ).catch(() => {});
      }
    });
  }

  return template;
}

async function showContextMenu() {
  const response = await fetch(`${backendUrl}/api/menu`, { cache: "no-store" });
  const commands = await response.json();
  const template = buildTemplate(nestCommands(commands));

  template.push({ type: "separator" });
  template.push({
    label: "退出",
    click: () => {
      if (win) {
        win.close();
      }
    }
  });

  const menu = Menu.buildFromTemplate(template);
  menu.popup({ window: win });
}

ipcMain.handle("shell:show-context-menu", async () => {
  await showContextMenu();
});

ipcMain.on("shell:drag-start", (_event, screenX, screenY) => {
  if (!win) {
    return;
  }

  const [windowX, windowY] = win.getPosition();
  dragOffset = {
    x: screenX - windowX,
    y: screenY - windowY
  };
});

ipcMain.on("shell:drag-move", (_event, screenX, screenY) => {
  if (!win || !dragOffset) {
    return;
  }

  win.setPosition(
    Math.round(screenX - dragOffset.x),
    Math.round(screenY - dragOffset.y)
  );
});

ipcMain.on("shell:drag-end", () => {
  dragOffset = null;
});

app.whenReady().then(createWindow);

app.on("window-all-closed", () => {
  app.quit();
});
