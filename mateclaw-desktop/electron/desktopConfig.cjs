const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const CONFIG_DIR = path.join(os.homedir(), '.metay-desktop');
const CONFIG_PATH = path.join(CONFIG_DIR, 'config.json');
const LEGACY_CONFIG_PATH = path.join(os.homedir(), '.yliyunclaw-desktop', 'config.json');
const DEV_DEFAULT_BACKEND_URL = 'http://127.0.0.1:18088';
const LEGACY_PACKAGED_BACKEND_URL = 'https://claw-demo.mate.vip';

function resolveForcedBackendUrl() {
  if (process.env.METAY_BACKEND_URL) {
    return process.env.METAY_BACKEND_URL;
  }
  if (process.env.YLIYUNCLAW_BACKEND_URL) {
    return process.env.YLIYUNCLAW_BACKEND_URL;
  }
  if (process.env.MATECLAW_BACKEND_URL) {
    return process.env.MATECLAW_BACKEND_URL;
  }
  return '';
}

function resolveDefaultBackendUrl(isPackaged = false) {
  const forcedBackendUrl = resolveForcedBackendUrl();
  if (forcedBackendUrl) {
    return forcedBackendUrl;
  }
  if (process.env.METAY_DEFAULT_BACKEND_URL) {
    return process.env.METAY_DEFAULT_BACKEND_URL;
  }
  if (process.env.YLIYUNCLAW_DEFAULT_BACKEND_URL) {
    return process.env.YLIYUNCLAW_DEFAULT_BACKEND_URL;
  }
  if (process.env.MATECLAW_DEFAULT_BACKEND_URL) {
    return process.env.MATECLAW_DEFAULT_BACKEND_URL;
  }
  return isPackaged ? 'http://meta.ylicloud.com:8080' : 'http://127.0.0.1:18088';
}

function createDefaultConfig(isPackaged = false) {
  return Object.freeze({
    backendUrl: resolveDefaultBackendUrl(isPackaged),
    proxyUrl: '',
    autoStartBackend: false,
    backendCommand: '',
    backendArgs: [],
    backendCwd: '',
    updatedAt: null,
  });
}

function ensureConfigDir() {
  fs.mkdirSync(CONFIG_DIR, { recursive: true });
}

function migratePackagedBackendConfig(input = {}, options = {}) {
  if (options.isPackaged !== true) {
    return input;
  }

  const packagedDefaultBackendUrl = resolveDefaultBackendUrl(true);
  const backendUrl = String(input.backendUrl || '').trim();
  const proxyUrl = String(input.proxyUrl || '').trim();
  const next = { ...input };

  if (!backendUrl || backendUrl === DEV_DEFAULT_BACKEND_URL || backendUrl === LEGACY_PACKAGED_BACKEND_URL) {
    next.backendUrl = packagedDefaultBackendUrl;
  }

  if (proxyUrl && (proxyUrl === packagedDefaultBackendUrl || proxyUrl === next.backendUrl)) {
    next.proxyUrl = '';
  }

  return next;
}

function normalizeConfig(input = {}, options = {}) {
  const defaultConfig = createDefaultConfig(options.isPackaged === true);
  const migratedInput = migratePackagedBackendConfig(input, options);
  return {
    backendUrl: String(migratedInput.backendUrl || defaultConfig.backendUrl).trim() || defaultConfig.backendUrl,
    proxyUrl: String(migratedInput.proxyUrl || defaultConfig.proxyUrl).trim(),
    autoStartBackend: Boolean(migratedInput.autoStartBackend),
    backendCommand: String(migratedInput.backendCommand || '').trim(),
    backendArgs: Array.isArray(migratedInput.backendArgs)
      ? migratedInput.backendArgs.map((item) => String(item)).filter(Boolean)
      : [],
    backendCwd: String(migratedInput.backendCwd || '').trim(),
    updatedAt: new Date().toISOString(),
  };
}

function loadDesktopConfig(options = {}) {
  const defaultConfig = createDefaultConfig(options.isPackaged === true);
  const forcedBackendUrl = resolveForcedBackendUrl();
  ensureConfigDir();
  if (!fs.existsSync(CONFIG_PATH)) {
    if (fs.existsSync(LEGACY_CONFIG_PATH)) {
      try {
        const legacy = JSON.parse(fs.readFileSync(LEGACY_CONFIG_PATH, 'utf-8'));
        const migrated = normalizeConfig({ ...defaultConfig, ...legacy }, options);
        if (forcedBackendUrl) {
          migrated.backendUrl = forcedBackendUrl;
        }
        fs.writeFileSync(CONFIG_PATH, JSON.stringify(migrated, null, 2), 'utf-8');
        return migrated;
      } catch {
        // Fall through to a fresh default config.
      }
    }
    fs.writeFileSync(CONFIG_PATH, JSON.stringify({ ...defaultConfig }, null, 2), 'utf-8');
    return { ...defaultConfig };
  }

  try {
    const raw = fs.readFileSync(CONFIG_PATH, 'utf-8');
    const parsed = JSON.parse(raw);
    const merged = { ...defaultConfig, ...normalizeConfig(parsed, options) };
    if (forcedBackendUrl) {
      merged.backendUrl = forcedBackendUrl;
    }
    if (JSON.stringify(parsed) !== JSON.stringify(merged)) {
      fs.writeFileSync(CONFIG_PATH, JSON.stringify(merged, null, 2), 'utf-8');
    }
    return merged;
  } catch {
    fs.writeFileSync(CONFIG_PATH, JSON.stringify({ ...defaultConfig }, null, 2), 'utf-8');
    return { ...defaultConfig };
  }
}

function saveDesktopConfig(input = {}, options = {}) {
  ensureConfigDir();
  const config = normalizeConfig({ ...loadDesktopConfig(options), ...input }, options);
  fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), 'utf-8');
  return config;
}

/**
 * WP-1 Desktop Settings Bridge
 *
 * Allows the desktop app to fetch the server-side settings introspection endpoint
 * (GET /api/v1/templates/settings/introspection) when a backend is configured.
 * Falls back gracefully when backend is unavailable or the endpoint is not yet deployed.
 *
 * @param {object} options
 * @param {string} options.backendUrl - the configured backend URL (from loadDesktopConfig)
 * @returns {Promise<object|null>} the effective settings map, or null on failure
 */
async function fetchServerSettingsIntrospection(options = {}) {
  const backendUrl = (options && options.backendUrl) || '';
  if (!backendUrl || backendUrl.startsWith('http://127.0.0.1') === false) {
    // Only auto-fetch for local dev backend to avoid leaking non-local backend configs
    return null;
  }
  try {
    const url = backendUrl.replace(/\/$/, '') + '/api/v1/templates/settings/introspection?scope=global';
    // Uses the built-in fetch (Node 18+ / Electron 28+)
    const response = await fetch(url, { signal: AbortSignal.timeout(3000) });
    if (!response.ok) return null;
    const json = await response.json();
    return json.data || null;
  } catch {
    return null;
  }
}

module.exports = {
  CONFIG_DIR,
  CONFIG_PATH,
  defaultConfig: createDefaultConfig(),
  createDefaultConfig,
  loadDesktopConfig,
  saveDesktopConfig,
  fetchServerSettingsIntrospection,
};
























