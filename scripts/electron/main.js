const { app, Tray, Menu, ipcMain, shell, dialog } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const fs = require('fs');
const fetch = require('node-fetch');

const store = require('./src/config/store');
const { isMas, getBackendMode, normalizeBaseUrl } = require('./src/config/defaults');
const { resolveBackendTarget, ensureModeAllowed } = require('./src/backend/backend-mode');
const { waitForCloudBackend } = require('./src/backend/cloud-backend');
const { createMainWindow, createSettingsWindow } = require('./src/window/main-window');
const { registerConfigIpc } = require('./src/ipc/config-ipc');

let mainWindow = null;
let tray = null;
let backendProcess = null;
let isQuitting = false;
const isDev = process.env.ELECTRON_DEV === '1';

const APP_ROOT = __dirname;
const JAR_DIR = isDev
  ? path.join(__dirname, '../../social-sdk-xianyu-manager/target')
  : path.join(process.resourcesPath, 'app');
const DATA_DIR = path.join(app.getPath('userData'));
const LOG_DIR = path.join(DATA_DIR, 'logs');

function ensureLogDir() {
  if (!fs.existsSync(LOG_DIR)) fs.mkdirSync(LOG_DIR, { recursive: true });
}

function logToFile(level, msg) {
  ensureLogDir();
  const ts = new Date().toISOString();
  const line = `[${ts}] ${level}: ${msg}\n`;
  fs.appendFileSync(path.join(LOG_DIR, 'electron.log'), line);
  console.log(`[${level}] ${msg}`);
}

function ensureDataDirs() {
  ['data', 'data/openlist', 'chrome-profiles', 'logs', 'config', 'uploads'].forEach(d => {
    const p = path.join(DATA_DIR, d);
    if (!fs.existsSync(p)) fs.mkdirSync(p, { recursive: true });
  });
}

function findJar() {
  if (!fs.existsSync(JAR_DIR)) return null;
  const files = fs.readdirSync(JAR_DIR).filter(f => f.endsWith('.jar'));

  // 策略1: 找固定命名的 fat jar（如 fix8.jar）
  const fixedFat = files.find(f => /fix\d+\.jar$/i.test(f));
  if (fixedFat && /BOOT-INF\//.test(fs.readFileSync(path.join(JAR_DIR, fixedFat)).toString('binary'))) return path.join(JAR_DIR, fixedFat);

  // 策略2: 找所有候选 jar，按 BOOT-INF 内容校验
  const candidates = files.filter(f => !/^(server|.*-sources|.*-javadoc|.*\.original)\.jar$/i.test(f));
  for (const f of candidates) {
    try {
      const content = fs.readFileSync(path.join(JAR_DIR, f)).toString('binary');
      if (/BOOT-INF\/classes\/META-INF\/MANIFEST\.MF/.test(content) || /BOOT-INF\/pom.properties/.test(content)) {
        return path.join(JAR_DIR, f);
      }
    } catch {}
  }

  // 策略3: 按大小排序，选最大的（通常 fat jar 最大）
  if (candidates.length > 0) {
    candidates.sort((a, b) => {
      const sa = fs.statSync(path.join(JAR_DIR, a)).size;
      const sb = fs.statSync(path.join(JAR_DIR, b)).size;
      return sb - sa;
    });
    return path.join(JAR_DIR, candidates[0]);
  }

  return files[0] ? path.join(JAR_DIR, files[0]) : null;
}

function javaExecutable() {
  const configured = store.get('javaPath', 'java');
  const bundled = process.platform === 'darwin'
    ? path.join(process.resourcesPath || '', 'jre', 'Contents', 'Home', 'bin', 'java')
    : process.platform === 'win32'
      ? path.join(process.resourcesPath || '', 'jre', 'bin', 'java.exe')
      : path.join(process.resourcesPath || '', 'jre', 'bin', 'java');
  if (!isDev && fs.existsSync(bundled)) return bundled;
  return configured || 'java';
}

function getLocalBaseUrl() {
  return `http://127.0.0.1:${Number(store.get('lastPort', 8080)) || 8080}`;
}

function startBackend() {
  if (getBackendMode(store) !== 'local') {
    logToFile('INFO', '云端模式：跳过本地后端启动');
    return;
  }
  if (backendProcess) {
    logToFile('INFO', '后端进程已运行');
    return;
  }

  const jarPath = findJar();
  if (!jarPath) {
    logToFile('ERROR', `未找到 JAR 文件，目录: ${JAR_DIR}`);
    dialog.showErrorBox('启动失败', `未找到后端 JAR 文件\n期望目录: ${JAR_DIR}\n\n请先运行 Maven 构建或检查打包资源。`);
    return;
  }

  ensureDataDirs();
  const port = Number(store.get('lastPort', 8080)) || 8080;
  const dataPath = path.join(DATA_DIR, 'data', 'xianyu-manager.db');
  const uploadsDir = path.join(DATA_DIR, 'uploads');
  const javaPath = javaExecutable();
  const args = [
    '-Xmx512m',
    '-Xms256m',
    `-Dserver.port=${port}`,
    '-Dfile.encoding=UTF-8',
    `-Duser.dir=${DATA_DIR}`,
    `-DDB_PATH=${dataPath}`,
    `-Dlogging.file.path=${LOG_DIR}`,
    `-Dspring.web.resources.static-locations=classpath:/static/,file:${uploadsDir}/`,
    '-jar', jarPath
  ];

  logToFile('INFO', `启动本地后端: ${javaPath} ${args.join(' ')}`);
  backendProcess = spawn(javaPath, args, {
    cwd: DATA_DIR,
    env: { ...process.env, JAVA_HOME: process.env.JAVA_HOME || '' }
  });

  backendProcess.stdout.on('data', data => logToFile('BACKEND-OUT', data.toString().trim()));
  backendProcess.stderr.on('data', data => logToFile('BACKEND-ERR', data.toString().trim()));
  backendProcess.on('exit', code => {
    logToFile('INFO', `后端进程退出，代码: ${code}`);
    backendProcess = null;
    if (!isQuitting && code !== 0 && getBackendMode(store) === 'local') {
      dialog.showErrorBox('后端异常退出', `后端进程已退出 (code: ${code})\n请查看日志: ${LOG_DIR}`);
    }
  });
}

async function waitForLocalBackend(port, timeout = 60000) {
  const start = Date.now();
  const urls = [
    `http://127.0.0.1:${port}/actuator/health`,
    `http://127.0.0.1:${port}/`
  ];
  while (Date.now() - start < timeout) {
    for (const url of urls) {
      try {
        const res = await fetch(url, { timeout: 5000 });
        if (res.status < 500) return true;
      } catch {}
    }
    await new Promise(r => setTimeout(r, 1000));
  }
  return false;
}

async function loadBackendUi() {
  const target = resolveBackendTarget(store);
  if (!mainWindow) return;
  mainWindow.loadFile(path.join(__dirname, 'renderer/loading.html'));

  if (target.mode === 'cloud') {
    const ready = await waitForCloudBackend(target.baseUrl);
    if (ready.ready && mainWindow) {
      mainWindow.loadURL(target.baseUrl);
      logToFile('INFO', `云端后台已就绪，加载 Web UI: ${target.baseUrl}`);
    } else {
      logToFile('ERROR', ready.reason || '云端后台不可达');
      dialog.showErrorBox('云端后台不可达', `${ready.reason || '请检查后台基础 URL'}\n可在设置中切换为本地模式或修改云端地址。`);
      openSettings();
    }
    return;
  }

  const port = Number(store.get('lastPort', 8080)) || 8080;
  const ready = await waitForLocalBackend(port);
  if (ready && mainWindow) {
    mainWindow.loadURL(getLocalBaseUrl());
    logToFile('INFO', `本地后端已就绪，加载 Web UI: ${getLocalBaseUrl()}`);
  } else {
    dialog.showErrorBox('本地后端启动超时', `请查看日志: ${LOG_DIR}`);
  }
}

function createWindow() {
  mainWindow = createMainWindow({
    store,
    preloadPath: path.join(__dirname, 'preload.js'),
    iconPath: path.join(__dirname, 'icons/icon.png')
  });

  mainWindow.on('close', e => {
    if (!isQuitting && store.get('minimizeToTray') && !isMas) {
      e.preventDefault();
      mainWindow.hide();
    }
  });
  mainWindow.on('closed', () => { mainWindow = null; });
}

function createTray() {
  if (isMas) return;
  const iconPath = path.join(__dirname, 'icons/tray.png');
  if (!fs.existsSync(iconPath)) return;
  tray = new Tray(iconPath);
  tray.setToolTip('闲鱼管理器');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: '打开主界面', click: () => { if (mainWindow) mainWindow.show(); } },
    { label: '访问 Web UI', click: () => shell.openExternal(resolveBackendTarget(store).baseUrl || getLocalBaseUrl()) },
    { type: 'separator' },
    { label: '重启后端', click: restartBackend, enabled: getBackendMode(store) === 'local' },
    { label: '查看日志', click: () => shell.openPath(LOG_DIR) },
    { label: '设置', click: openSettings },
    { type: 'separator' },
    { label: '退出', click: quitApp }
  ]));
  tray.on('double-click', () => { if (mainWindow) mainWindow.show(); });
}

function openSettings() {
  createSettingsWindow({
    parent: mainWindow,
    preloadPath: path.join(__dirname, 'preload.js'),
    htmlPath: path.join(__dirname, 'renderer/settings.html')
  });
}

function stopBackend() {
  if (!backendProcess) return;
  backendProcess.kill();
  backendProcess = null;
}

function restartBackend() {
  if (getBackendMode(store) !== 'local') {
    loadBackendUi();
    return;
  }
  stopBackend();
  startBackend();
  loadBackendUi();
}

function quitApp() {
  isQuitting = true;
  stopBackend();
  app.quit();
}

function getBackendStatus() {
  const target = resolveBackendTarget(store);
  return {
    mode: target.mode,
    running: target.mode === 'local' ? !!backendProcess : !!target.baseUrl,
    pid: backendProcess?.pid,
    baseUrl: target.baseUrl,
    appStoreSafe: target.appStoreSafe
  };
}

app.whenReady().then(() => {
  ensureModeAllowed(store);
  logToFile('INFO', `闲鱼管理器启动 | 平台: ${process.platform} | 模式: ${getBackendMode(store)} | 数据目录: ${DATA_DIR}`);
  createWindow();
  createTray();
  registerConfigIpc({ ipcMain, store, dataDir: DATA_DIR, shell, getBackendStatus, restartBackend, app });
  startBackend();
  loadBackendUi();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin' && !tray) app.quit();
});

app.on('activate', () => {
  if (!mainWindow) {
    createWindow();
    loadBackendUi();
  } else {
    mainWindow.show();
  }
});

app.on('before-quit', () => {
  isQuitting = true;
});
