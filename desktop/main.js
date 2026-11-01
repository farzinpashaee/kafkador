// Kafkador as a desktop app: runs the Spring Boot jar on a bundled Java runtime, on this machine only, and shows its
// UI in a window. Nothing here knows about Kafka; the jar is the same one the Docker image runs.
const { app, BrowserWindow, dialog, shell } = require('electron');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const http = require('node:http');
const net = require('node:net');
const path = require('node:path');

const HOST = '127.0.0.1';
// The same port each run where possible: the UI's remembered settings (theme, menu) belong to its address.
const PREFERRED_PORT = 18080;
const STARTUP_TIMEOUT_MS = 180_000;

// Packaged, the jar and runtime are extra resources; with "npm start", they're in stage/.
const resources = app.isPackaged ? process.resourcesPath : path.join(__dirname, 'stage');

let server;
let quitting = false;

function javaPath() {
  return path.join(resources, 'runtime', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
}

function portIsFree(port) {
  return new Promise(resolve => {
    const probe = net.createServer();
    probe.once('error', () => resolve(0));
    probe.listen(port, HOST, () => {
      const free = probe.address().port;
      probe.close(() => resolve(free));
    });
  });
}

async function choosePort() {
  return (await portIsFree(PREFERRED_PORT)) || portIsFree(0);
}

/** Starts Kafkador. Its working folder holds ./data, so it lives with the user's app data. */
function startServer(port, logFile) {
  const log = fs.openSync(logFile, 'w');
  server = spawn(javaPath(), [
    '-XX:MaxRAMPercentage=25.0',
    '-jar', path.join(resources, 'kafkador.jar'),
    `--server.port=${port}`,
    `--server.address=${HOST}`,
    '--kafkador.setup.auto=true'
  ], { cwd: app.getPath('userData'), stdio: ['ignore', log, log], windowsHide: true });
  server.once('exit', code => {
    if (quitting) return;
    fail(`Kafkador stopped unexpectedly (exit code ${code}).`, logFile);
  });
  server.once('error', error => fail(`Kafkador could not start: ${error.message}`, logFile));
}

function isHealthy(port) {
  return new Promise(resolve => {
    const request = http.get({ host: HOST, port, path: '/actuator/health', timeout: 2000 }, response => {
      response.resume();
      resolve(response.statusCode === 200);
    });
    request.once('timeout', () => request.destroy());
    request.once('error', () => resolve(false));
  });
}

async function waitUntilHealthy(port) {
  const deadline = Date.now() + STARTUP_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (await isHealthy(port)) return true;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  return false;
}

function stopServer() {
  if (server && server.exitCode === null) server.kill();
}

function fail(message, logFile) {
  quitting = true;
  stopServer();
  dialog.showErrorBox('Kafkador', `${message}\n\nThe log is at:\n${logFile}`);
  app.quit();
}

function createWindow(origin) {
  const window = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 900,
    minHeight: 600,
    title: 'Kafkador',
    autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true }
  });

  // Only Kafkador's own pages stay in the window; links elsewhere open in the browser.
  const openOutside = url => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url);
  };
  window.webContents.setWindowOpenHandler(({ url }) => {
    openOutside(url);
    return { action: 'deny' };
  });
  window.webContents.on('will-navigate', (event, url) => {
    if (url.startsWith('file:') || new URL(url).origin === origin) return;
    event.preventDefault();
    openOutside(url);
  });
  return window;
}

async function launch() {
  const logDir = path.join(app.getPath('userData'), 'logs');
  fs.mkdirSync(logDir, { recursive: true });
  const logFile = path.join(logDir, 'kafkador.log');

  const port = await choosePort();
  const origin = `http://${HOST}:${port}`;
  const window = createWindow(origin);
  await window.loadFile(path.join(__dirname, 'loading.html'));

  startServer(port, logFile);
  if (await waitUntilHealthy(port)) {
    if (!quitting) await window.loadURL(origin);
  } else if (!quitting) {
    fail('Kafkador took too long to start.', logFile);
  }
}

// One copy at a time: the embedded database can only be opened once.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    const [window] = BrowserWindow.getAllWindows();
    if (window) {
      if (window.isMinimized()) window.restore();
      window.focus();
    }
  });
  app.whenReady().then(launch);
  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', () => {
    quitting = true;
    stopServer();
  });
}
