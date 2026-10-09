export function money(value) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '—';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value));
}

export function percent(value) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '—';
  return `${Number(value) > 0 ? '+' : ''}${Number(value).toFixed(2)}%`;
}

export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
}

export function orderValidation({ stock, quantity, side, wallet, holdings, portfolioAvailable = true }) {
  if (!stock || !Number.isFinite(Number(stock.price)) || Number(stock.price) <= 0) return 'Select a stock with an available price.';
  if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > 2147483647) return 'Enter a whole-number quantity between 1 and 2,147,483,647.';
  if (!['BUY', 'SELL'].includes(side)) return 'Select buy or sell.';
  if (side === 'BUY') {
    if (wallet === null || wallet === undefined || !Number.isFinite(Number(wallet))) return 'Refresh your wallet before placing a buy order.';
    if (Number(stock.price) * quantity > Number(wallet)) return 'Your available funds are below the estimated order total.';
  }
  if (side === 'SELL') {
    if (!portfolioAvailable) return 'Refresh your holdings before placing a sell order.';
    const owned = Number(holdings.find(item => item.symbol === stock.symbol)?.quantity || 0);
    if (quantity > owned) return `You hold ${owned} shares. Reduce the sell quantity.`;
  }
  return '';
}

export function validAmount(value, { allowZero = false } = {}) {
  const raw = String(value);
  const amount = Number(raw);
  return /^\d+(?:\.\d{1,2})?$/.test(raw) && Number.isFinite(amount) && (allowZero ? amount >= 0 : amount > 0) && amount <= 9999999999.99;
}

export function getErrorMessage(data, status) {
  const value = data?.errorMessage || data?.message || data?.detail;
  if (typeof value === 'string' && value.trim()) return value.slice(0, 1500);
  if (Array.isArray(value)) return value.map(item => item.msg || 'Invalid input').join('; ').slice(0, 1500);
  if (status === 401) return 'Your session has expired. Sign in again.';
  if (status === 403) return 'This action is not permitted for your account.';
  if (status === 404) return 'The requested account, stock, or order was not found.';
  if (status === 400 || status === 422) return 'Check the entered details and try again.';
  return `The request failed (${status}). Please try again.`;
}

export function emptyPortfolio() {
  return { holdings: [], currentValue: 0, totalInvested: 0, totalPnl: 0, totalPnlPercent: 0 };
}
