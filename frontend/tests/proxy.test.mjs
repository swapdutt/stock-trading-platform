import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { once } from 'node:events';
import { createFrontendServer } from '../server.mjs';
import { TradingApi } from '../public/api.js';

async function listen(server) { server.listen(0, '127.0.0.1'); await once(server, 'listening'); return `http://127.0.0.1:${server.address().port}`; }
async function close(server) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }

test('all UI API methods use the exact controller contracts through the proxy', async () => {
  const received = [];
  const gateway = http.createServer(async (req, res) => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    received.push({ method: req.method, path: req.url, headers: req.headers, body: Buffer.concat(chunks).toString() });
    res.writeHead(200, { 'Content-Type': 'application/json' }); res.end('{"ok":true}');
  });
  const gatewayUrl = await listen(gateway);
  const frontend = createFrontendServer({ gatewayUrl });
  const url = await listen(frontend);
  const nativeFetch = globalThis.fetch;
  globalThis.fetch = (input, options) => nativeFetch(new URL(input, url), options);
  try {
    const api = new TradingApi(); api.token = 'signed-jwt';
    await api.login({ email: 'test@example.com', password: 'secret1' });
    await api.register({ firstName: 'A', lastName: 'B', email: 'test@example.com', password: 'secret1', initialDeposit: 10000 });
    await api.profile(); await api.stocks(); await api.quote('TCS'); await api.portfolio(); await api.orders(); await api.order('order-id');
    await api.placeOrder({ symbol: 'TCS', orderType: 'BUY', quantity: 2 });
    await api.addFunds('user-id', '50.25');
    assert.deepEqual(received.map(item => [item.method, item.path]), [
      ['POST', '/api/v1/users/login'], ['POST', '/api/v1/users/register'], ['GET', '/api/v1/users/userDetail'],
      ['GET', '/api/v1/market/stocks'], ['GET', '/api/v1/market/stocks/TCS'], ['GET', '/api/v1/portfolio/current-portfolio'],
      ['GET', '/api/v1/orders/current-order-list'], ['GET', '/api/v1/orders/order-id'], ['POST', '/api/v1/orders/'],
      ['POST', '/api/v1/users/user-id/funds/add?amount=50.25']
    ]);
    assert.equal(received[0].headers.authorization, undefined);
    assert.equal(received[2].headers.authorization, 'Bearer signed-jwt');
    assert.deepEqual(JSON.parse(received[8].body), { symbol: 'TCS', orderType: 'BUY', quantity: 2 });
    const request = await nativeFetch(`${url}/api/v1/users/userDetail`, { headers: { 'X-User-Id': 'spoofed', Authorization: 'Bearer signed-jwt' } });
    assert.equal(request.status, 200); assert.equal(received.at(-1).headers['x-user-id'], undefined);
    assert.equal((await nativeFetch(`${url}/api/v1/users/user-id/funds/credit`, { method: 'POST' })).status, 404);
    assert.equal((await nativeFetch(`${url}/api/v1/users/login`, { method: 'POST', headers: { Origin: 'https://unrelated.example' } })).status, 403);
    assert.equal((await nativeFetch(`${url}/server.mjs`)).status, 404);
  } finally { globalThis.fetch = nativeFetch; await close(frontend); await close(gateway); }
});

test('empty unauthorized responses remain unauthorized and unreachable gateways fail without replay', async () => {
  let calls = 0;
  const gateway = http.createServer((req, res) => { calls++; res.writeHead(401); res.end(); });
  const gatewayUrl = await listen(gateway);
  const frontend = createFrontendServer({ gatewayUrl, timeoutMs: 100 });
  const url = await listen(frontend);
  try {
    assert.equal((await fetch(`${url}/api/v1/market/stocks`)).status, 401);
    assert.equal(calls, 1);
    await close(gateway);
    const response = await fetch(`${url}/api/v1/orders/`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"symbol":"TCS","orderType":"BUY","quantity":1}' });
    assert.equal(response.status, 502);
    assert.match((await response.json()).errorMessage, /refresh/i);
    assert.equal(calls, 1);
  } finally { await close(frontend); if (gateway.listening) await close(gateway); }
});
