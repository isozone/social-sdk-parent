function registerConfigIpc({ ipcMain, store, dataDir, shell, getBackendStatus, restartBackend, app }) {
  ipcMain.handle('get-config', () => store.store);
  ipcMain.handle('set-config', (event, key, value) => {
    store.set(key, value);
    return true;
  });
  ipcMain.handle('get-data-path', () => dataDir);
  ipcMain.handle('open-data-folder', () => shell.openPath(dataDir));
  ipcMain.handle('get-backend-status', () => getBackendStatus());
  ipcMain.handle('restart-backend', () => restartBackend());
  ipcMain.handle('relaunch-app', () => {
    app.relaunch();
    app.quit();
  });
}

module.exports = { registerConfigIpc };
