import http from 'node:http';
import https from 'node:https';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const publicDir = fileURLToPath(new URL('./public/', import.meta.url));
const staticFiles = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/index.html', ['index.html', 'text/html; charset=utf-8']],
  ['/styles.css', ['styles.css', 'text/css; charset=utf-8']],
  ['/app.js', ['app.js', 'text/javascript; charset=utf-8']],
  ['/api.js', ['api.js', 'text/javascript; charset=utf-8']],
  ['/demo.js', ['demo.js', 'text/javascript; charset=utf-8']],
  ['/trading.js', ['trading.js', 'text/javascript; charset=utf-8']],
  ['/favicon.svg', ['favicon.svg', 'image/svg+xml']]
]);

function json(res, status, value) {
  if (res.destroyed || res.writableEnded) return;
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(value));
}

export function createFrontendServer({ gatewayUrl = 'http://localhost:8080', timeoutMs = 45000 } = {}) {
  const gateway = new URL(gatewayUrl);
  if (!['http:', 'https:'].includes(gateway.protocol) || gateway.username || gateway.password ||
      gateway.pathname !== '/' || gateway.search || gateway.hash) {
    throw new Error('GATEWAY_URL must be an HTTP(S) origin, for example http://localhost:8080.');
  }

  return http.createServer(async (req, res) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");

    let url;
    try { url = new URL(req.url, 'http://frontend.local'); }
    catch { return json(res, 400, { errorMessage: 'Invalid request URL.' }); }

    // Reject browser writes from other origins; tokens are never read from cookies.
    if (!['GET', 'HEAD'].includes(req.method) && req.headers.origin) {
      try {
        if (new URL(req.headers.origin).host !== req.headers.host) {
          return json(res, 403, { errorMessage: 'Cross-origin requests are not allowed.' });
        }
      } catch { return json(res, 403, { errorMessage: 'Invalid request origin.' }); }
    }

    if (url.pathname.startsWith('/api/v1/')) {
      if (!['GET', 'POST'].includes(req.method)) {
        return json(res, 405, { errorMessage: 'Method not supported.' });
      }
      // These are the exact routes used by this UI. Do not expose a generic proxy.
      const allowed = req.method === 'GET'
        ? /^\/api\/v1\/(users\/userDetail|market\/stocks(?:\/[^/]+)?|orders\/(current-order-list|[^/]+)|portfolio\/current-portfolio)$/
        : /^\/api\/v1\/(users\/(login|register|[^/]+\/funds\/add)|orders\/)$/;
      if (!allowed.test(url.pathname)) {
        return json(res, 404, { errorMessage: 'API route not available in this front end.' });
      }
      // Paths are forwarded exactly, including the required slash in POST /orders/.
      const target = new URL(url.pathname + url.search, gateway.origin);
      const chunks = [];
      let size = 0;
      try {
        for await (const chunk of req) {
          size += chunk.length;
          if (size > 64 * 1024) return json(res, 413, { errorMessage: 'Request is too large.' });
          chunks.push(chunk);
        }
      } catch { return json(res, 400, { errorMessage: 'Could not read request.' }); }
      const body = Buffer.concat(chunks);
      const headers = { accept: 'application/json' };
      if (req.headers.authorization) headers.authorization = req.headers.authorization;
      if (body.length) {
        headers['content-type'] = req.headers['content-type'] || 'application/json';
        headers['content-length'] = body.length;
      }
      // X-User-Id is deliberately omitted: the gateway derives it from the JWT.
      const transport = gateway.protocol === 'https:' ? https : http;
      const upstream = transport.request(target, { method: req.method, headers }, response => {
        res.writeHead(response.statusCode || 502, {
          'Content-Type': response.headers['content-type'] || 'application/json; charset=utf-8',
          'Cache-Control': 'no-store'
        });
        response.pipe(res);
        response.on('error', () => res.destroy());
      });
      upstream.setTimeout(timeoutMs, () => upstream.destroy(new Error('Gateway request timed out')));
      upstream.on('error', () => {
        if (res.headersSent) return res.destroy();
        json(res, 502, { errorMessage: 'Cannot reach the API gateway. Check that the backend services are running. If a trade or deposit was submitted, refresh its result before retrying.' });
      });
      res.on('close', () => upstream.destroy());
      upstream.end(body);
      return;
    }

    if (!['GET', 'HEAD'].includes(req.method)) return json(res, 405, { errorMessage: 'Method not supported.' });
    if (url.pathname === '/frontend-config') return json(res, 200, { gatewayUrl: gateway.origin, pollIntervalMs: 5000 });
    const file = staticFiles.get(url.pathname);
    if (!file) return json(res, 404, { errorMessage: 'File not found.' });
    try {
      const bytes = await readFile(path.join(publicDir, file[0]));
      res.writeHead(200, { 'Content-Type': file[1], 'Cache-Control': 'no-cache' });
      res.end(req.method === 'HEAD' ? undefined : bytes);
    } catch { json(res, 500, { errorMessage: 'Could not load the front end.' }); }
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 5173);
  const host = process.env.HOST || '127.0.0.1';
  const server = createFrontendServer({ gatewayUrl: process.env.GATEWAY_URL || 'http://localhost:8080' });
  server.listen(port, host, () => console.log(`Trading dashboard: http://${host}:${port}`));
  server.on('error', error => { console.error(error.message); process.exitCode = 1; });
}
