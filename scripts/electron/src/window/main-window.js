const { BrowserWindow, shell } = require('electron');
const path = require('path');

function createMainWindow({ store, preloadPath, iconPath }) {
  const bounds = store.get('windowBounds');
  const mainWindow = new BrowserWindow({
    width: bounds.width,
    height: bounds.height,
    minWidth: 900,
    minHeight: 600,
    title: '闲鱼管理器',
    icon: iconPath,
    webPreferences: {
      preload: preloadPath,
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: false,
      webSecurity: true,
      allowRunningInsecureContent: false
    },
    show: false
  });

  mainWindow.once('ready-to-show', () => {
    if (!store.get('startInTray')) mainWindow.show();
  });

  mainWindow.on('resize', () => {
    const b = mainWindow.getBounds();
    store.set('windowBounds', { width: b.width, height: b.height });
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  return mainWindow;
}

function createSettingsWindow({ parent, preloadPath, htmlPath }) {
  const settingsWindow = new BrowserWindow({
    width: 560,
    height: 720,
    parent,
    modal: true,
    resizable: false,
    title: '设置',
    webPreferences: {
      preload: preloadPath,
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: false
    }
  });
  settingsWindow.loadFile(htmlPath);
  return settingsWindow;
}

module.exports = {
  createMainWindow,
  createSettingsWindow
};
