import { getErrorMessage } from './trading.js';

export class ApiError extends Error {
  constructor(message, status = 0) { super(message); this.status = status; }
}

export class TradingApi {
  constructor() { this.token = null; }
  clear() { this.token = null; }

  async request(path, { method = 'GET', body, publicRequest = false } = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 50000);
    const headers = { Accept: 'application/json' };
    if (!publicRequest && this.token) headers.Authorization = `Bearer ${this.token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    try {
      const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal, cache: 'no-store', credentials: 'omit' });
      const text = await response.text();
      let data = null;
      if (text) { try { data = JSON.parse(text); } catch { data = null; } }
      if (!response.ok) throw new ApiError(getErrorMessage(data, response.status), response.status);
      if (!data) throw new ApiError('The backend returned an empty or invalid response.', response.status);
      return data;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      if (error.name === 'AbortError') throw new ApiError('The request timed out. Refresh orders and your wallet before retrying a trade or deposit.');
      throw new ApiError('Unable to connect. Check the frontend server and API gateway.');
    } finally { clearTimeout(timer); }
  }

  login(body) { return this.request('/api/v1/users/login', { method: 'POST', body, publicRequest: true }); }
  register(body) { return this.request('/api/v1/users/register', { method: 'POST', body, publicRequest: true }); }
  profile() { return this.request('/api/v1/users/userDetail'); }
  stocks() { return this.request('/api/v1/market/stocks'); }
  quote(symbol) { return this.request(`/api/v1/market/stocks/${encodeURIComponent(symbol)}`); }
  portfolio() { return this.request('/api/v1/portfolio/current-portfolio'); }
  orders() { return this.request('/api/v1/orders/current-order-list'); }
  order(id) { return this.request(`/api/v1/orders/${encodeURIComponent(id)}`); }
  placeOrder(body) { return this.request('/api/v1/orders/', { method: 'POST', body }); }
  addFunds(userId, amount) { return this.request(`/api/v1/users/${encodeURIComponent(userId)}/funds/add?amount=${encodeURIComponent(amount)}`, { method: 'POST' }); }
}
