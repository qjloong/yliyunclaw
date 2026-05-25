const { app, BrowserWindow, Menu, dialog, ipcMain, shell } = require('electron')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const http = require('node:http')
const https = require('node:https')
const { createDefaultConfig, loadDesktopConfig, saveDesktopConfig } = require('./desktopConfig.cjs')
const { BackendManager } = require('./backendManager.cjs')
const { createLocalServer } = require('./localServer.cjs')
const {
  listWorkspaceTree,
  globWorkspaceFiles,
  grepWorkspaceFiles,
  readWorkspaceSnippet,
  writeWorkspacePatch,
  getGitStatus,
  getGitDiff,
  runReadonlyCommand,
  prepareApprovalCommand,
  approvePreparedCommand,
  denyPreparedCommand,
  executeApprovedCommand,
} = require('./localToolHost.cjs')

const isDev = !app.isPackaged
const preloadPath = path.join(__dirname, 'preload.cjs')
const maxLoadRetries = 20
const loadRetryDelayMs = 1500

let mainWindow = null
let proxyConfigWindow = null
let localServerInfo = null
let desktopConfig = loadDesktopConfig({ isPackaged: app.isPackaged })

const backendManager = new BackendManager()

function isHttpUrl(value) {
  return /^https?:\/\//i.test(value || '')
}

function getBackendUrl() {
  return desktopConfig.backendUrl
}

function getProxyUrl() {
  return String(desktopConfig.proxyUrl || '').trim()
}

function getApiTargetUrl() {
  return getProxyUrl() || getBackendUrl()
}

function buildDesktopUrl(routePath = '/') {
  if (!localServerInfo?.url) {
    return null
  }
  const base = localServerInfo.url.replace(/\/$/, '')
  const targetPath = String(routePath || '/').trim() || '/'
  const normalizedPath = targetPath.startsWith('/') ? targetPath : `/${targetPath}`
  return `${base}${normalizedPath}`
}

function openDesktopRoute(routePath = '/') {
  const targetUrl = buildDesktopUrl(routePath)
  if (!targetUrl || !mainWindow || mainWindow.isDestroyed()) {
    return
  }
  void mainWindow.loadURL(targetUrl)
}

function isAllowedWindowUrl(targetUrl) {
  const appUrl = localServerInfo?.url
  if (appUrl && typeof targetUrl === 'string' && targetUrl.startsWith(appUrl)) {
    return true
  }
  return typeof targetUrl === 'string' && targetUrl.startsWith('data:text/html')
}

function attachWindowNavigationGuards(window) {
  window.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })

  window.webContents.on('will-navigate', (event, url) => {
    if (!isAllowedWindowUrl(url)) {
      event.preventDefault()
      if (isHttpUrl(url)) {
        shell.openExternal(url)
      }
    }
  })
}

function createDesktopProxyWindow() {
  const targetUrl = buildDesktopUrl('/desktop-proxy')
  if (!targetUrl) {
    return
  }

  if (proxyConfigWindow && !proxyConfigWindow.isDestroyed()) {
    proxyConfigWindow.show()
    proxyConfigWindow.focus()
    void proxyConfigWindow.loadURL(targetUrl)
    return
  }

  proxyConfigWindow = new BrowserWindow({
    width: 640,
    height: 500,
    minWidth: 560,
    minHeight: 420,
    show: false,
    resizable: true,
    maximizable: false,
    minimizable: false,
    modal: true,
    parent: mainWindow || undefined,
    autoHideMenuBar: true,
    title: 'Meta Y - 代理配置',
    backgroundColor: '#f3f8ff',
    icon: path.resolve(__dirname, '../../mateclaw-ui/public/logo/mateclaw_logo.png'),
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      spellcheck: false,
    },
  })

  attachWindowNavigationGuards(proxyConfigWindow)

  proxyConfigWindow.once('ready-to-show', () => {
    proxyConfigWindow?.show()
    proxyConfigWindow?.focus()
  })

  proxyConfigWindow.on('closed', () => {
    proxyConfigWindow = null
  })

  void proxyConfigWindow.loadURL(targetUrl)
}

function buildFallbackHtml(targetUrl, errorMessage) {
  const escapedUrl = String(targetUrl || '').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const escapedError = String(errorMessage || 'Unknown error').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <title>Meta Y Desktop</title>
    <style>
      body { margin: 0; font-family: Inter, "Segoe UI", sans-serif; background: #fff7ed; color: #1f2937; display: grid; place-items: center; min-height: 100vh; }
      .card { width: min(92vw, 720px); background: white; border: 1px solid rgba(217, 119, 87, 0.18); border-radius: 18px; padding: 28px; box-shadow: 0 24px 48px rgba(15, 23, 42, 0.12); }
      h1 { margin: 0 0 10px; font-size: 24px; }
      p, li { line-height: 1.7; color: #4b5563; }
      code { display: inline-block; padding: 2px 6px; border-radius: 8px; background: #f8fafc; border: 1px solid #e5e7eb; }
      .error { margin-top: 14px; padding: 12px; border-radius: 12px; background: #fff1f2; border: 1px solid #fecdd3; color: #be123c; white-space: pre-wrap; }
    </style>
  </head>
  <body>
    <div class="card">
      <h1>Meta Y Desktop 无法连接本地界面</h1>
      <p>当前桌面客户端尝试打开 <code>${escapedUrl}</code>。</p>
      <ul>
        <li>开发模式：先启动 <code>mateclaw-ui</code>，再执行 <code>pnpm dev:ui</code>。</li>
        <li>打包模式：请先执行桌面渲染构建，或重新运行打包命令。</li>
        <li>如需配置代理地址，请在应用顶部菜单中打开代理配置页面。</li>
      </ul>
      <div class="error">${escapedError}</div>
    </div>
  </body>
</html>`
}

async function loadApp(window, attempt = 0) {
  const startUrl = localServerInfo?.url
  try {
    if (!isHttpUrl(startUrl)) {
      throw new Error(`Unsupported desktop URL: ${startUrl}`)
    }
    await window.loadURL(startUrl)
  } catch (error) {
    if (attempt + 1 < maxLoadRetries && !window.isDestroyed()) {
      setTimeout(() => {
        if (!window.isDestroyed()) {
          void loadApp(window, attempt + 1)
        }
      }, loadRetryDelayMs)
      return
    }
    const html = buildFallbackHtml(startUrl, error instanceof Error ? error.message : String(error || 'Unknown error'))
    await window.loadURL(`data:text/html;charset=UTF-8,${encodeURIComponent(html)}`)
  }
}

function createAppMenu(window) {
  const template = [
    {
      label: 'Meta Y',
      submenu: [
        { role: 'reload', label: '重新加载' },
        { role: 'forceReload', label: '强制刷新' },
        { role: 'toggleDevTools', label: '开发者工具' },
        { type: 'separator' },
        {
          label: '配置代理地址',
          click: () => createDesktopProxyWindow(),
        },
        { type: 'separator' },
        { role: 'quit', label: '退出' },
      ],
    },
    {
      label: 'View',
      submenu: [
        { role: 'resetZoom', label: '实际大小' },
        { role: 'zoomIn', label: '放大' },
        { role: 'zoomOut', label: '缩小' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: '全屏' },
      ],
    },
    {
      label: 'Help',
      submenu: [
        {
          label: '当前连接地址',
          click: () => {
            if (localServerInfo?.url) {
              shell.openExternal(localServerInfo.url)
            }
          },
        },
      ],
    },
  ]

  const menu = Menu.buildFromTemplate(template)
  Menu.setApplicationMenu(menu)

  if (isDev && window?.webContents) {
    window.webContents.openDevTools({ mode: 'detach' })
  }
}

function requestJson(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith('https://') ? https : http
    const req = client.get(url, (res) => {
      const chunks = []
      res.on('data', (chunk) => chunks.push(chunk))
      res.on('end', () => {
        const body = Buffer.concat(chunks).toString('utf-8')
        resolve({
          ok: (res.statusCode || 500) < 400,
          status: res.statusCode || 500,
          body,
        })
      })
    })
    req.on('error', reject)
  })
}

function postJson(url, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const target = new URL(url)
    const client = target.protocol === 'https:' ? https : http
    const payload = JSON.stringify(body ?? {})
    const req = client.request(
      target,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload),
          ...headers,
        },
      },
      (res) => {
        const chunks = []
        res.on('data', (chunk) => chunks.push(chunk))
        res.on('end', () => {
          const responseBody = Buffer.concat(chunks).toString('utf-8')
          resolve({
            ok: (res.statusCode || 500) < 400,
            status: res.statusCode || 500,
            body: responseBody,
          })
        })
      },
    )
    req.on('error', reject)
    req.write(payload)
    req.end()
  })
}

async function registerIpcHandlers() {
  ipcMain.handle('mateclaw-desktop:get-runtime-info', async () => ({
    isDesktop: true,
    isPackaged: app.isPackaged,
    electronVersion: process.versions.electron,
    localAppUrl: localServerInfo?.url || null,
    backendUrl: getBackendUrl(),
    proxyUrl: getProxyUrl(),
    apiTargetUrl: getApiTargetUrl(),
    localToolHost: {
      available: true,
      mode: 'desktop-only',
      capabilities: ['workspace.tree', 'workspace.glob', 'workspace.grep', 'workspace.read_snippet', 'workspace.write_patch', 'git.status', 'git.diff', 'command.run.readonly', 'command.run.approval'],
      sharedPayloadSchema: 'desktop-local-tool-result.v1',
      harnessIngest: true,
    },
    platform: process.platform,
    arch: process.arch,
    homeDir: os.homedir(),
  }))

  ipcMain.handle('mateclaw-desktop:get-server-config', async () => loadDesktopConfig({ isPackaged: app.isPackaged }))

  ipcMain.handle('mateclaw-desktop:save-server-config', async (_event, payload) => {
    desktopConfig = saveDesktopConfig(payload || {}, { isPackaged: app.isPackaged })
    await backendManager.ensureStarted(desktopConfig)
    desktopConfig = loadDesktopConfig({ isPackaged: app.isPackaged })
    return desktopConfig
  })

  ipcMain.handle('mateclaw-desktop:test-server', async (_event, payload) => {
    const targetUrl = String(payload?.proxyUrl || payload?.backendUrl || getApiTargetUrl()).replace(/\/$/, '')
    const candidates = [`${targetUrl}/actuator/health`, `${targetUrl}/api/v1/auth/login`]
    let lastError = null

    for (const candidate of candidates) {
      try {
        const result = await requestJson(candidate)
        return {
          success: true,
          status: result.status,
          url: candidate,
          body: result.body,
        }
      } catch (error) {
        lastError = error
      }
    }

    return {
      success: false,
      message: lastError instanceof Error ? lastError.message : String(lastError || 'Server unreachable'),
      url: targetUrl,
    }
  })

  ipcMain.handle('mateclaw-desktop:select-directory', async () => {
    const result = await dialog.showOpenDialog(mainWindow || undefined, {
      properties: ['openDirectory', 'createDirectory'],
    })
    if (result.canceled || !result.filePaths.length) {
      return null
    }
    return result.filePaths[0]
  })

  ipcMain.handle('mateclaw-desktop:select-files', async (_event, options) => {
    const result = await dialog.showOpenDialog(mainWindow || undefined, {
      properties: ['openFile', 'multiSelections'],
      filters: options?.filters,
    })
    if (result.canceled) {
      return []
    }
    return result.filePaths
  })

  ipcMain.handle('mateclaw-desktop:read-text-file', async (_event, filePath) => {
    return fs.readFileSync(filePath, 'utf-8')
  })

  ipcMain.handle('mateclaw-desktop:workspace-tree', async (_event, options) => {
    return listWorkspaceTree(options || {})
  })

  ipcMain.handle('mateclaw-desktop:workspace-glob', async (_event, options) => {
    return globWorkspaceFiles(options || {})
  })

  ipcMain.handle('mateclaw-desktop:workspace-grep', async (_event, options) => {
    return grepWorkspaceFiles(options || {})
  })

  ipcMain.handle('mateclaw-desktop:read-file-snippet', async (_event, options) => {
    return readWorkspaceSnippet(options || {})
  })

  ipcMain.handle('mateclaw-desktop:write-workspace-patch', async (_event, options) => {
    return writeWorkspacePatch(options || {})
  })

  ipcMain.handle('mateclaw-desktop:git-status', async (_event, options) => {
    return getGitStatus(options || {})
  })

  ipcMain.handle('mateclaw-desktop:git-diff', async (_event, options) => {
    return getGitDiff(options || {})
  })

  ipcMain.handle('mateclaw-desktop:run-readonly-command', async (_event, options) => {
    return runReadonlyCommand(options || {})
  })

  ipcMain.handle('mateclaw-desktop:prepare-approval-command', async (_event, options) => {
    return prepareApprovalCommand(options || {})
  })

  ipcMain.handle('mateclaw-desktop:approve-command-request', async (_event, options) => {
    return approvePreparedCommand(options || {})
  })

  ipcMain.handle('mateclaw-desktop:deny-command-request', async (_event, options) => {
    return denyPreparedCommand(options || {})
  })

  ipcMain.handle('mateclaw-desktop:execute-approved-command', async (_event, options) => {
    return executeApprovedCommand(options || {})
  })

  ipcMain.handle('mateclaw-desktop:ingest-harness-tool-result', async (_event, options) => {
    const runId = String(options?.runId || '').trim()
    if (!runId) {
      throw new Error('runId is required')
    }
    const targetUrl = `${String(options?.backendUrl || getBackendUrl()).replace(/\/$/, '')}/api/v1/harness/runs/${encodeURIComponent(runId)}/desktop-tool-results`
    const headers = {}
    if (options?.authToken) {
      headers.Authorization = `Bearer ${String(options.authToken).trim()}`
    }
    if (options?.workspaceId != null && String(options.workspaceId).trim() !== '') {
      headers['X-Workspace-Id'] = String(options.workspaceId).trim()
    }
    const result = await postJson(targetUrl, options?.payload || {}, headers)
    let parsedBody = result.body
    try {
      parsedBody = JSON.parse(result.body)
    } catch {
      // keep raw body when response is not json
    }
    return {
      success: result.ok,
      status: result.status,
      url: targetUrl,
      body: parsedBody,
    }
  })

  ipcMain.handle('mateclaw-desktop:write-text-file', async (_event, payload) => {
    fs.mkdirSync(path.dirname(payload.filePath), { recursive: true })
    fs.writeFileSync(payload.filePath, payload.content, 'utf-8')
    return true
  })

  ipcMain.handle('mateclaw-desktop:reveal-path', async (_event, targetPath) => {
    if (targetPath) {
      shell.showItemInFolder(targetPath)
    }
    return true
  })

  ipcMain.handle('mateclaw-desktop:open-path', async (_event, targetPath) => {
    if (targetPath) {
      await shell.openPath(targetPath)
    }
    return true
  })

  ipcMain.handle('mateclaw-desktop:close-current-window', async (event) => {
    const targetWindow = BrowserWindow.fromWebContents(event.sender)
    if (targetWindow && !targetWindow.isDestroyed()) {
      targetWindow.close()
      return true
    }
    return false
  })
}

async function startDesktopServices() {
  desktopConfig = loadDesktopConfig({ isPackaged: app.isPackaged })
  await backendManager.ensureStarted(desktopConfig)
  localServerInfo = await createLocalServer({
    rendererUrl: process.env.MATECLAW_RENDERER_URL || null,
    getApiTargetUrl,
  })
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 1200,
    minHeight: 760,
    show: false,
    autoHideMenuBar: false,
    title: 'Meta Y Desktop',
    backgroundColor: '#fff7ed',
    icon: path.resolve(__dirname, '../../mateclaw-ui/public/logo/mateclaw_logo.png'),
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      spellcheck: false,
    },
  })

  createAppMenu(mainWindow)
  attachWindowNavigationGuards(mainWindow)

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show()
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  void loadApp(mainWindow)
}

app.whenReady().then(() => {
  void registerIpcHandlers()

  startDesktopServices()
    .then(() => {
      createWindow()
    })
    .catch(async (error) => {
      createWindow()
      if (mainWindow && !mainWindow.isDestroyed()) {
        const html = buildFallbackHtml('local-server', error instanceof Error ? error.message : String(error || 'Unknown error'))
        await mainWindow.loadURL(`data:text/html;charset=UTF-8,${encodeURIComponent(html)}`)
      }
    })

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('before-quit', () => {
  void backendManager.stop()
  if (proxyConfigWindow && !proxyConfigWindow.isDestroyed()) {
    proxyConfigWindow.close()
  }
  if (localServerInfo?.server) {
    localServerInfo.server.close()
  }
})
