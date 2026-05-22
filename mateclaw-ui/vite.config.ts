import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'
import packageJson from './package.json'

export default defineConfig(({ mode }) => {
  const isDesktop = mode === 'desktop'
  const env = loadEnv(mode, process.cwd(), '')
  const desktopProxyTarget = String(env.MATECLAW_UI_PROXY_TARGET || '').trim()
  const backendProxyTarget = String(env.MATECLAW_BACKEND_URL || '').trim()
  const apiProxyTarget = desktopProxyTarget
    || (isDesktop ? 'http://127.0.0.1:17737' : '')
    || backendProxyTarget
    || 'http://localhost:18088'

  return {
    define: {
      'window.__APP_VERSION__': JSON.stringify(packageJson.version),
    },
    plugins: [
      vue(),
      tailwindcss(),
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
          ws: true,
        },
      },
    },
    build: {
      outDir: isDesktop ? '../mateclaw-desktop/renderer-dist' : '../mateclaw-server/src/main/resources/static',
      emptyOutDir: true,
    },
  }
})
