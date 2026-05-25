const http = require('node:http');
const path = require('node:path');
const handler = require('serve-handler');
const httpProxy = require('http-proxy');

const DEFAULT_PORT = 17737;

function resolvePreferredPort() {
  const raw = process.env.METAY_DESKTOP_PORT || process.env.YLIYUNCLAW_DESKTOP_PORT || process.env.MATECLAW_DESKTOP_PORT;
  const parsed = Number.parseInt(String(raw || ''), 10);
  return Number.isInteger(parsed) && parsed > 0 && parsed < 65536 ? parsed : DEFAULT_PORT;
}

function createLocalServer({ rendererUrl, getApiTargetUrl, onReady }) {
  const proxy = httpProxy.createProxyServer({
    changeOrigin: true,
    ws: true,
    xfwd: false,
  });

  const publicPath = path.join(__dirname, '..', 'renderer-dist');

  proxy.on('proxyReq', (proxyReq, req) => {
    const target = String(req.__metayProxyTarget || '').trim();
    if (req.url?.startsWith('/api/')) {
      proxyReq.removeHeader('forwarded');
      proxyReq.removeHeader('x-forwarded-for');
      proxyReq.removeHeader('x-forwarded-host');
      proxyReq.removeHeader('x-forwarded-port');
      proxyReq.removeHeader('x-forwarded-proto');
      proxyReq.removeHeader('origin');
      proxyReq.removeHeader('referer');
    }
    if (target) {
      proxyReq.setHeader('X-MetaY-Desktop-Proxy-Target', target);
      proxyReq.setHeader('X-MetaY-Desktop-Proxy-Mode', 'desktop-local-proxy');
    }
  });

  proxy.on('proxyRes', (proxyRes, req) => {
    const target = String(req.__metayProxyTarget || '').trim();
    if (target && proxyRes?.headers) {
      proxyRes.headers['x-metay-desktop-proxy-target'] = target;
      proxyRes.headers['x-metay-desktop-proxy-mode'] = 'desktop-local-proxy';
    }
  });

  const server = http.createServer((req, res) => {
    if (req.url?.startsWith('/api/')) {
      const target = getApiTargetUrl();
      req.__metayProxyTarget = target;
      proxy.web(req, res, { target });
      return;
    }

    if (rendererUrl) {
      proxy.web(req, res, { target: rendererUrl });
      return;
    }

    handler(req, res, {
      public: publicPath,
      cleanUrls: true,
      rewrites: [{ source: '**', destination: '/index.html' }],
    });
  });

  server.on('upgrade', (req, socket, head) => {
    const target = req.url?.startsWith('/api/') ? getApiTargetUrl() : rendererUrl;
    if (!target) {
      socket.destroy();
      return;
    }
    req.__metayProxyTarget = target;
    proxy.ws(req, socket, head, { target });
  });

  proxy.on('error', (error, req, res) => {
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    }

    res.end(
      JSON.stringify({
        success: false,
        message: 'Desktop proxy request failed',
        detail: error.message,
        path: req.url,
      }),
    );
  });

  return new Promise((resolve, reject) => {
    const preferredPort = resolvePreferredPort();
    let fallbackTried = false;

    server.once('error', (error) => {
      if (!fallbackTried && error && error.code === 'EADDRINUSE') {
        fallbackTried = true;
        server.listen(0, '127.0.0.1');
        return;
      }
      reject(error);
    });

    server.listen(preferredPort, '127.0.0.1', () => {
      const address = server.address();
      if (!address || typeof address === 'string') {
        reject(new Error('Unable to determine local desktop server port'));
        return;
      }

      const info = {
        server,
        url: `http://127.0.0.1:${address.port}`,
      };

      onReady?.(info);
      resolve(info);
    });
  });
}

module.exports = {
  createLocalServer,
};
