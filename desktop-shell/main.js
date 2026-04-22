const { app, BrowserWindow, Menu, Tray, ipcMain, nativeImage } = require("electron");
const path = require("path");

let win = null;
let tray = null;
let dragOffset = null;
let backendWatchdog = null;
let backendFailureCount = 0;
let forceQuit = false;

const BACKEND_PING_INTERVAL_MS = 3000;
const BACKEND_PING_TIMEOUT_MS = 1500;
const BACKEND_MAX_FAILURES = 2;
const TRAY_TOOLTIP = "Desktop Pet";
const TRAY_ICON_SIZE = 16;

const backendUrl = (() => {
  const value = process.argv.find((arg) => arg.startsWith("--backend="));
  return value ? value.slice("--backend=".length) : "http://127.0.0.1:18080";
})();

function stopBackendWatchdog() {
  if (backendWatchdog) {
    clearInterval(backendWatchdog);
    backendWatchdog = null;
  }
}

function requestAppQuit() {
  forceQuit = true;
  app.quit();
}

function trayIconPath() {
  return path.resolve(
    __dirname,
    "..",
    "src",
    "main",
    "resources",
    "live2d",
    "atri",
    "icon.jpg"
  );
}

function createTrayIcon() {
  const image = nativeImage.createFromPath(trayIconPath());
  if (image.isEmpty()) {
    return nativeImage.createEmpty();
  }

  return image.resize({
    width: TRAY_ICON_SIZE,
    height: TRAY_ICON_SIZE
  });
}

function isWindowVisible() {
  return Boolean(win && !win.isDestroyed() && win.isVisible());
}

function showWindow() {
  if (!win || win.isDestroyed()) {
    return;
  }

  if (win.isMinimized()) {
    win.restore();
  }

  win.show();
  win.focus();
  updateTrayMenu();
}

function hideToTray() {
  if (!win || win.isDestroyed()) {
    return;
  }

  win.hide();
  updateTrayMenu();
}

function updateTrayMenu() {
  if (!tray) {
    return;
  }

  const visible = isWindowVisible();
  const menu = Menu.buildFromTemplate([
    {
      label: visible ? "隐藏到后台" : "显示桌宠",
      click: () => {
        if (isWindowVisible()) {
          hideToTray();
          return;
        }
        showWindow();
      }
    },
    {
      label: "退出",
      click: () => {
        requestAppQuit();
      }
    }
  ]);

  tray.setContextMenu(menu);
  tray.setToolTip(visible ? `${TRAY_TOOLTIP} - 运行中` : `${TRAY_TOOLTIP} - 已隐藏`);
}

function createTray() {
  if (tray) {
    return;
  }

  tray = new Tray(createTrayIcon());
  tray.on("click", () => {
    showWindow();
  });
  updateTrayMenu();
}

async function pingBackend() {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), BACKEND_PING_TIMEOUT_MS);

  try {
    const response = await fetch(`${backendUrl}/api/state`, {
      cache: "no-store",
      signal: controller.signal
    });

    if (!response.ok) {
      throw new Error(`backend returned ${response.status}`);
    }

    backendFailureCount = 0;
  } catch (error) {
    backendFailureCount += 1;
    console.error(`[BACKEND] ping failed (${backendFailureCount}/${BACKEND_MAX_FAILURES}):`, error.message);
    if (backendFailureCount >= BACKEND_MAX_FAILURES) {
      stopBackendWatchdog();
      requestAppQuit();
    }
  } finally {
    clearTimeout(timeoutId);
  }
}

function startBackendWatchdog() {
  stopBackendWatchdog();
  backendFailureCount = 0;
  backendWatchdog = setInterval(() => {
    pingBackend().catch(() => {});
  }, BACKEND_PING_INTERVAL_MS);
}

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
  startBackendWatchdog();

  win.webContents.on("did-fail-load", (_event, code, description) => {
    console.error(`[BACKEND] page load failed: ${code} ${description}`);
    requestAppQuit();
  });

  win.on("close", (event) => {
    if (forceQuit) {
      return;
    }

    event.preventDefault();
    hideToTray();
  });

  win.on("show", () => {
    updateTrayMenu();
  });

  win.on("hide", () => {
    updateTrayMenu();
  });

  win.on("closed", () => {
    stopBackendWatchdog();
    fetch(`${backendUrl}/api/exit`, { method: "POST" }).catch(() => {});
    win = null;
    updateTrayMenu();
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
        ).catch(() => {
          requestAppQuit();
        });
      }
    });
  }

  return template;
}

async function showContextMenu() {
  try {
    const response = await fetch(`${backendUrl}/api/menu`, { cache: "no-store" });
    if (!response.ok) {
      throw new Error(`backend returned ${response.status}`);
    }

    const commands = await response.json();
    const template = buildTemplate(nestCommands(commands));

    template.push({ type: "separator" });
    template.push({
      label: "隐藏到后台",
      click: () => {
        hideToTray();
      }
    });
    template.push({
      label: "退出",
      click: () => {
        requestAppQuit();
      }
    });

    const menu = Menu.buildFromTemplate(template);
    menu.popup({ window: win });
  } catch (error) {
    console.error("[BACKEND] context menu fetch failed:", error.message);
    requestAppQuit();
  }
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

app.whenReady().then(() => {
  createTray();
  createWindow();
});

app.on("before-quit", () => {
  forceQuit = true;
  stopBackendWatchdog();
});

app.on("window-all-closed", () => {
  if (forceQuit) {
    app.quit();
  }
});

app.on("activate", () => {
  showWindow();
});
