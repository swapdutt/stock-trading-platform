import { TradingApi, ApiError } from './api.js';
import { DemoAccount } from './demo.js';
import { money, percent, escapeHtml as esc, orderValidation, validAmount } from './trading.js';

const $ = id => document.getElementById(id);
const api = new TradingApi();
const demo = new DemoAccount();
const state = {
  mode: 'demo', profile: demo.profile, stocks: demo.stocks, portfolio: demo.portfolio(), orders: demo.orders,
  symbol: 'RELIANCE', side: 'BUY', view: 'overview', paused: false, pending: false, refreshing: false,
  portfolioAvailable: true, profileAvailable: true, stocksAvailable: true, epoch: 0, history: new Map(),
  review: null, authMode: 'login', gatewayUrl: 'http://localhost:8080', pollIntervalMs: 5000, lastUpdate: new Date(),
  portfolioPending: null
};
let toastTimer;

const iconPaths = {
  grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  chart: '<path d="M4 4v16h16M7 14l4-4 4 3 5-7"/>',
  briefcase: '<rect x="3" y="7" width="18" height="14" rx="2"/><path d="M8 7V4h8v3M3 12c6 3 12 3 18 0M10 13h4"/>',
  list: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/>',
  layers: '<path d="m12 3 9 5-9 5-9-5 9-5ZM3 12l9 5 9-5M3 16l9 5 9-5"/>',
  wallet: '<path d="M20 8H5a2 2 0 0 1 0-4h14v4M3 6v13a2 2 0 0 0 2 2h15V8M20 12h-5v5h5"/>',
  refresh: '<path d="M20 7v5h-5M4 17v-5h5M5 7a8 8 0 0 1 13-2l2 3M4 16l2 3a8 8 0 0 0 13-2"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>',
  search: '<circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/>',
  exchange: '<path d="M4 8h16M4 16h16M16 4l4 4-4 4M8 12l-4 4 4 4"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  detail: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>'
};
const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${iconPaths[name] || iconPaths.info}</svg>`;
document.querySelectorAll('[data-icon]').forEach(el => { el.innerHTML = icon(el.dataset.icon); });

function toast(message) {
  clearTimeout(toastTimer);
  $('toast').textContent = message;
  $('toast').hidden = false;
  toastTimer = setTimeout(() => { $('toast').hidden = true; }, 6000);
}

function showError(id, message) {
  $(id).textContent = message || '';
  $(id).hidden = !message;
}

function dateTime(value) {
  if (!value) return 'Not recorded';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Not recorded' : date.toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

function signedMoney(value) {
  if (value === null || value === undefined) return '—';
  return `${Number(value) > 0 ? '+' : ''}${money(value)}`;
}

function signClass(value) { return Number(value) >= 0 ? 'positive' : 'negative'; }
const statuses = { EXECUTED: 'Executed', FLAGGED: 'Flagged', AI_CHECK: 'AI check', PENDING: 'Pending', FAILED: 'Failed' };
function statusBadge(status) {
  return `<span class="status-badge ${statuses[status] ? status.toLowerCase() : 'unknown'}">${esc(statuses[status] || status || 'Unknown')}</span>`;
}

function selectedStock() { return state.stocks.find(stock => stock.symbol === state.symbol); }
function heldShares(symbol = state.symbol) { return Number(state.portfolio?.holdings?.find(item => item.symbol === symbol)?.quantity || 0); }

function validate(quantity = Number($('order-quantity').value), stock = selectedStock()) {
  if (state.pending) return 'An account action is being processed.';
  if (!state.stocksAvailable) return 'Refresh quotes before placing an order.';
  if (state.side === 'SELL' && state.portfolioPending) return 'Wait for your previous order to update the holdings, then refresh.';
  return orderValidation({ stock, quantity, side: state.side, wallet: state.profileAvailable ? state.profile?.walletBalance : null, holdings: state.portfolio?.holdings || [], portfolioAvailable: state.portfolioAvailable });
}

function renderSummary() {
  const p = state.portfolio;
  $('wallet-value').textContent = money(state.profile?.walletBalance);
  $('portfolio-value').textContent = money(p?.currentValue);
  $('invested-value').textContent = money(p?.totalInvested);
  $('return-value').textContent = signedMoney(p?.totalPnl);
  $('return-value').className = p ? signClass(p.totalPnl) : '';
  $('return-percent').textContent = p ? `${percent(p.totalPnlPercent)} on invested capital` : 'Portfolio unavailable';
  $('return-percent').className = `summary-foot ${p ? signClass(p.totalPnl) : ''}`;
  const count = p?.holdings?.length || 0;
  $('holdings-caption').textContent = p ? `${count} ${count === 1 ? 'stock' : 'stocks'} in your portfolio` : 'Portfolio unavailable';
  $('holdings-count').textContent = `${count} ${count === 1 ? 'stock' : 'stocks'}`;
  $('order-count').textContent = state.orders.length;
  const name = [state.profile?.firstName, state.profile?.lastName].filter(Boolean).join(' ') || 'Trader';
  $('side-name').textContent = name;
  $('side-avatar').textContent = `${state.profile?.firstName?.[0] || 'T'}${state.profile?.lastName?.[0] || ''}`.toUpperCase();
  $('side-account').textContent = state.mode === 'demo' ? 'Demo account' : 'Connected account';
  $('mode-tag').textContent = state.mode === 'demo' ? 'Demo mode' : 'Backend connected';
  $('mode-tag').className = `mode-tag ${state.mode === 'live' ? 'connected' : ''}`;
  $('connection-button').textContent = state.mode === 'demo' ? 'Connect backend' : 'Account';
  $('workspace-notice').hidden = state.mode !== 'demo';
  $('connection-button').disabled = state.pending;
  $('notice-connect').disabled = state.pending;
  $('add-funds-button').disabled = state.pending;
  $('refresh-button').disabled = state.refreshing || state.pending;
}

function renderChart(stock) {
  const samples = state.mode === 'demo' ? demo.history(stock.symbol) : (state.history.get(stock.symbol) || []);
  $('chart-caption').textContent = state.mode === 'demo' ? 'Illustrative demo history' : 'Observed quotes · This session';
  if (samples.length < 2) {
    $('quote-chart').innerHTML = '<div class="chart-empty">Collecting quotes for this session…</div>';
    return;
  }
  const width = 620, height = 106;
  const min = Math.min(...samples), max = Math.max(...samples);
  const padding = Math.max((max - min) * .22, stock.price * .0005);
  const range = max - min + 2 * padding;
  const points = samples.map((value, i) => [i / (samples.length - 1) * width, height - ((value - min + padding) / range) * height]);
  const line = points.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(2)},${y.toFixed(2)}`).join(' ');
  const last = points.at(-1);
  const grid = [25, 55, 85].map(y => `<path d="M0 ${y}H${width}" stroke="#ffffff" stroke-opacity=".065" stroke-dasharray="3 6"/>`).join('');
  $('quote-chart').innerHTML = `<svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" role="img" aria-label="${esc(stock.symbol)} ${state.mode === 'demo' ? 'illustrative demo price history' : 'quotes observed in this browser session'}"><defs><linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#6ee4c4" stop-opacity=".2"/><stop offset="100%" stop-color="#6ee4c4" stop-opacity="0"/></linearGradient></defs>${grid}<path d="${line} L${width},${height} L0,${height} Z" fill="url(#chart-fill)"/><path d="${line}" fill="none" stroke="#82e9cb" stroke-width="2.2" vector-effect="non-scaling-stroke"/><circle cx="${last[0]}" cy="${last[1]}" r="3" fill="#c4ffe8"/></svg>`;
}

function renderQuote() {
  const stock = selectedStock();
  $('quote-label').textContent = state.mode === 'demo' ? 'SAMPLE QUOTE' : 'SIMULATED QUOTE';
  $('quote-symbol').textContent = stock?.symbol || 'No stock selected';
  $('quote-name').textContent = stock?.companyName || 'Quotes will appear when available';
  $('quote-monogram').textContent = stock?.symbol?.slice(0, 2) || '—';
  $('quote-price').textContent = money(stock?.price);
  $('quote-change').textContent = stock ? `${signedMoney(stock.change)} (${percent(stock.changePercent)})` : '';
  $('quote-change').className = stock ? signClass(stock.change) : '';
  if (stock) renderChart(stock);
  else $('quote-chart').innerHTML = '<div class="chart-empty">No quotes available</div>';
  $('stock-facts').innerHTML = stock ? [['Open', money(stock.open)], ['High', money(stock.high)], ['Low', money(stock.low)], ['Volume', Number(stock.volume || 0).toLocaleString('en-IN')]].map(([name, value]) => `<div><dt>${name}</dt><dd>${value}</dd></div>`).join('') : '<div><dt>Quotes unavailable</dt><dd>—</dd></div>';
}

function renderMarket() {
  const query = $('market-search').value.toLowerCase().trim();
  const stocks = state.stocks.filter(stock => `${stock.symbol} ${stock.companyName}`.toLowerCase().includes(query));
  $('market-rows').innerHTML = stocks.map(stock => `<tr class="${stock.symbol === state.symbol ? 'selected-row' : ''}"><td><div class="stock-cell"><span class="stock-monogram">${esc(stock.symbol.slice(0, 2))}</span><button class="stock-name-button" data-symbol="${esc(stock.symbol)}"><strong>${esc(stock.symbol)}</strong><small>${esc(stock.companyName)}</small></button></div></td><td class="numeric">${money(stock.price)}</td><td class="numeric ${signClass(stock.changePercent)}">${percent(stock.changePercent)}</td><td><button class="table-trade" data-symbol="${esc(stock.symbol)}" aria-label="Trade ${esc(stock.symbol)}">Trade</button></td></tr>`).join('') || `<tr><td colspan="4" class="empty-state"><strong>${query ? 'No matching stocks' : 'No quotes available'}</strong>${query ? 'Try another symbol or company name.' : 'Refresh after the market-data service is running.'}</td></tr>`;
  $('quotes-update').textContent = state.lastUpdate ? `Updated ${state.lastUpdate.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}${state.paused ? ' · Paused' : ''}` : 'Waiting for quotes';
  $('poll-button').textContent = state.paused ? 'Resume updates' : 'Pause updates';
}

function renderOrderForm() {
  const stock = selectedStock();
  const oldOptions = [...$('order-symbol').options].map(option => option.value).join('|');
  if (oldOptions !== state.stocks.map(item => item.symbol).join('|')) {
    $('order-symbol').innerHTML = state.stocks.map(item => `<option value="${esc(item.symbol)}">${esc(item.symbol)} · ${esc(item.companyName)}</option>`).join('');
  }
  $('order-symbol').value = state.symbol;
  $('buy-button').setAttribute('aria-pressed', state.side === 'BUY');
  $('sell-button').setAttribute('aria-pressed', state.side === 'SELL');
  $('owned-shares').textContent = state.portfolioAvailable ? `${heldShares()} shares held` : 'Holdings unavailable';
  $('order-price').textContent = money(stock?.price);
  $('order-wallet').textContent = money(state.profile?.walletBalance);
  const quantity = Number($('order-quantity').value);
  $('order-total').textContent = stock && Number.isSafeInteger(quantity) && quantity > 0 ? money(Number(stock.price) * quantity) : '—';
  const error = validate(quantity, stock);
  $('order-validation').textContent = error;
  $('review-order-button').disabled = Boolean(error);
  $('review-order-button').className = `button button-primary submit-order ${state.side === 'SELL' ? 'sell' : ''}`;
  $('review-order-button').textContent = `Review ${state.side.toLowerCase()} order`;
  ['order-symbol', 'order-quantity', 'quantity-minus', 'quantity-plus', 'buy-button', 'sell-button'].forEach(id => { $(id).disabled = state.pending; });
}

function renderPortfolio() {
  $('holdings-rows').innerHTML = (state.portfolio?.holdings || []).map(holding => `<tr><td><div class="stock-cell"><span class="stock-monogram">${esc(holding.symbol.slice(0, 2))}</span><strong>${esc(holding.symbol)}</strong></div></td><td class="numeric">${Number(holding.quantity).toLocaleString('en-IN')}</td><td class="numeric">${money(holding.averageBuyPrice)}</td><td class="numeric">${money(holding.currentPrice)}</td><td class="numeric">${money(holding.currentValue)}</td><td class="numeric ${signClass(Number(holding.currentValue) - Number(holding.invested))}">${signedMoney(Number(holding.currentValue) - Number(holding.invested))}<small class="holding-percent"> (${percent(holding.pnlPercent)})</small></td><td><button class="table-trade" data-symbol="${esc(holding.symbol)}" data-sell="true" aria-label="Sell ${esc(holding.symbol)}">Sell</button></td></tr>`).join('') || `<tr><td colspan="7" class="empty-state"><strong>${state.portfolioAvailable ? 'Your portfolio starts here' : 'Portfolio unavailable'}</strong>${state.portfolioAvailable ? 'Buy your first stock to see your holdings.' : 'Check the portfolio service and refresh.'}</td></tr>`;
}

function renderOrders() {
  const all = state.view === 'orders';
  const filter = $('order-filter').value;
  const orders = all ? state.orders.filter(order => filter === 'ALL' || order.orderStatus === filter) : state.orders.slice(0, 4);
  $('orders-heading').textContent = all ? 'Order history' : 'Recent orders';
  $('all-orders-link').hidden = all;
  $('order-filter-wrap').hidden = !all;
  $('order-rows').innerHTML = orders.map(order => `<tr><td><div class="stock-cell"><strong>${esc(order.symbol)}<small>${esc(dateTime(order.createdAt))}</small></strong></div></td><td><span class="side-badge ${order.orderType === 'SELL' ? 'sell' : 'buy'}">${esc(order.orderType)}</span></td><td class="numeric">${Number(order.quantity).toLocaleString('en-IN')}</td><td class="numeric">${money(order.price)}</td><td class="numeric">${money(order.totalAmount)}</td><td>${statusBadge(order.orderStatus)}</td><td><button class="order-details-button" data-order-id="${esc(order.id)}" aria-label="Details for ${esc(order.symbol)} ${esc(order.orderType)} order">${icon('detail')}</button></td></tr>`).join('') || '<tr><td colspan="7" class="empty-state"><strong>No orders to show</strong>Your submitted orders will appear here.</td></tr>';
}

function renderView() {
  const views = { overview: ['Overview', 'A view of your investments.'], markets: ['Markets', 'Find your next trade.'], portfolio: ['Portfolio', 'Your holdings, at a glance.'], orders: ['Orders', 'Every trade, in one place.'] };
  const [name, title] = views[state.view];
  $('breadcrumb').textContent = name;
  $('page-title').textContent = title;
  $('greeting').textContent = state.mode === 'demo' ? 'YOUR TRADING DAY' : `${state.profile?.firstName || 'YOUR'}'S TRADING WORKSPACE`.toUpperCase();
  $('trading-workspace').hidden = !['overview', 'markets'].includes(state.view);
  $('portfolio-section').hidden = state.view !== 'portfolio';
  $('orders-section').hidden = !['overview', 'orders'].includes(state.view);
  document.querySelectorAll('[data-view]').forEach(el => {
    if (el.dataset.view === state.view) el.setAttribute('aria-current', 'page');
    else el.removeAttribute('aria-current');
  });
  document.title = `${name} · StockDesk`;
}

function render() { renderSummary(); renderView(); renderQuote(); renderMarket(); renderOrderForm(); renderPortfolio(); renderOrders(); }

function openDialog(id) {
  if (state.pending) return;
  $(id).showModal();
}
document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => {
  if (!state.pending) button.closest('dialog').close();
}));
document.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('cancel', event => {
  if (state.pending) event.preventDefault();
}));

function selectSymbol(symbol, side) {
  if (state.pending || !state.stocks.some(stock => stock.symbol === symbol)) return;
  state.symbol = symbol;
  if (side) state.side = side;
  render();
}

document.addEventListener('click', event => {
  const button = event.target.closest('[data-symbol]');
  if (!button) return;
  selectSymbol(button.dataset.symbol, button.dataset.sell ? 'SELL' : undefined);
  if (!['overview', 'markets'].includes(state.view)) location.hash = 'markets';
  $('order-quantity').focus({ preventScroll: true });
});

function syncView() {
  const view = location.hash.slice(1);
  state.view = ['overview', 'markets', 'portfolio', 'orders'].includes(view) ? view : 'overview';
  renderView(); renderOrders();
}
window.addEventListener('hashchange', syncView);
$('market-search').addEventListener('input', renderMarket);
$('order-filter').addEventListener('change', renderOrders);
$('order-symbol').addEventListener('change', event => selectSymbol(event.target.value));
$('order-quantity').addEventListener('input', renderOrderForm);
$('buy-button').addEventListener('click', () => { state.side = 'BUY'; renderOrderForm(); });
$('sell-button').addEventListener('click', () => { state.side = 'SELL'; renderOrderForm(); });
$('quantity-minus').addEventListener('click', () => { $('order-quantity').value = Math.max(1, Number($('order-quantity').value) - 1); renderOrderForm(); });
$('quantity-plus').addEventListener('click', () => { $('order-quantity').value = Math.min(2147483647, Math.max(0, Number($('order-quantity').value)) + 1); renderOrderForm(); });
document.addEventListener('keydown', event => {
  if (event.key === '/' && !event.ctrlKey && !event.metaKey && !event.altKey && !event.target.matches('input,textarea,select') && !document.querySelector('dialog[open]')) {
    event.preventDefault(); location.hash = 'markets'; $('market-search').focus();
  }
});

function syncDemo() {
  state.profile = demo.profile; state.stocks = demo.stocks; state.portfolio = demo.portfolio(); state.orders = demo.orders;
  state.portfolioAvailable = true; state.profileAvailable = true; state.stocksAvailable = true; state.lastUpdate = new Date();
}

function addObservedQuotes() {
  for (const stock of state.stocks) {
    const price = Number(stock.price);
    if (!Number.isFinite(price)) continue;
    const points = state.history.get(stock.symbol) || [];
    points.push(price);
    state.history.set(stock.symbol, points.slice(-60));
  }
}

function clearLiveSession(message) {
  state.epoch += 1; api.clear(); state.mode = 'demo'; state.symbol = 'RELIANCE'; state.side = 'BUY';
  state.history.clear(); state.portfolioPending = null; state.review = null;
  syncDemo(); render(); showError('connection-error', '');
  document.querySelectorAll('dialog[open]').forEach(dialog => dialog.close());
  if (message) toast(message);
}

async function refresh({ manual = false } = {}) {
  if (state.refreshing || (state.pending && !manual)) return;
  if (state.mode === 'demo') { demo.updateQuotes(); syncDemo(); render(); if (manual) toast('Demo workspace refreshed.'); return; }
  state.refreshing = true; renderSummary();
  const epoch = state.epoch;
  const reads = await Promise.allSettled([api.profile(), api.stocks(), api.portfolio(), api.orders()]);
  if (epoch !== state.epoch) { state.refreshing = false; renderSummary(); return; }
  if (reads.some(result => result.status === 'rejected' && result.reason.status === 401)) {
    state.refreshing = false; clearLiveSession('Your session expired. Sign in again to reconnect.'); return;
  }
  const errors = [];
  const names = ['Wallet', 'Quotes', 'Portfolio', 'Orders'];
  for (const [i, result] of reads.entries()) {
    if (result.status === 'rejected') { errors.push(`${names[i]}: ${result.reason.message}`); continue; }
    const data = result.value;
    if (i === 0 && data.id && Number.isFinite(Number(data.walletBalance))) state.profile = data;
    else if (i === 1 && Array.isArray(data)) {
      state.stocks = data.filter(stock => typeof stock.symbol === 'string' && Number.isFinite(Number(stock.price)));
      state.lastUpdate = new Date(); addObservedQuotes();
      if (!selectedStock()) state.symbol = state.stocks[0]?.symbol || '';
    } else if (i === 2 && Array.isArray(data.holdings)) {
      state.portfolio = data;
      if (state.portfolioPending && heldShares(state.portfolioPending.symbol) === state.portfolioPending.quantity) state.portfolioPending = null;
    } else if (i === 3 && Array.isArray(data)) state.orders = data;
    else { errors.push(`${names[i]}: unexpected backend response.`); reads[i] = { status: 'rejected' }; }
  }
  state.profileAvailable = reads[0].status === 'fulfilled';
  state.stocksAvailable = reads[1].status === 'fulfilled';
  state.portfolioAvailable = reads[2].status === 'fulfilled';
  if (errors.length) showError('connection-error', `${errors.join(' ')} Previously loaded values may be out of date.`);
  else showError('connection-error', '');
  state.refreshing = false; render();
  if (manual && !errors.length) toast('Account and quotes refreshed.');
}

$('refresh-button').addEventListener('click', () => { void refresh({ manual: true }); });
$('poll-button').addEventListener('click', () => { state.paused = !state.paused; renderMarket(); if (!state.paused) void refresh(); });

function setAuthMode(mode) {
  if (state.pending) return;
  state.authMode = mode;
  const register = mode === 'register';
  $('register-fields').hidden = !register; $('deposit-field').hidden = !register;
  for (const input of $('register-fields').querySelectorAll('input')) { input.disabled = !register; input.required = register; }
  $('deposit-field').querySelector('input').disabled = !register;
  $('auth-form').elements.password.autocomplete = register ? 'new-password' : 'current-password';
  $('login-tab').setAttribute('aria-pressed', !register); $('register-tab').setAttribute('aria-pressed', register);
  $('auth-submit').textContent = register ? 'Create account' : 'Sign in';
  showError('auth-error', '');
}

function openAccount() {
  if (state.mode === 'demo') { showError('auth-error', ''); $('gateway-caption').textContent = `Gateway: ${state.gatewayUrl}`; openDialog('auth-dialog'); }
  else { $('account-description').textContent = `${state.profile.firstName} ${state.profile.lastName} · ${state.profile.email}`; openDialog('disconnect-dialog'); }
}
$('connection-button').addEventListener('click', openAccount);
$('notice-connect').addEventListener('click', openAccount);
$('login-tab').addEventListener('click', () => setAuthMode('login'));
$('register-tab').addEventListener('click', () => setAuthMode('register'));
$('signout-button').addEventListener('click', () => { if (!state.pending) clearLiveSession('Signed out. You are back in demo mode.'); });

function setBusy(busy) {
  state.pending = busy;
  document.querySelectorAll('dialog button, dialog input').forEach(el => { el.disabled = busy; });
  if (!busy) {
    const register = state.authMode === 'register';
    $('register-fields').querySelectorAll('input').forEach(input => { input.disabled = !register; });
    $('deposit-field').querySelector('input').disabled = !register;
  }
  renderSummary(); renderOrderForm();
}

$('auth-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (state.pending) return;
  const fields = new FormData(event.target);
  const register = state.authMode === 'register';
  const body = { email: fields.get('email').trim(), password: fields.get('password') };
  if (register) {
    body.firstName = fields.get('firstName').trim(); body.lastName = fields.get('lastName').trim();
    if (!body.firstName || !body.lastName) return showError('auth-error', 'Enter your first and last name.');
    if (!validAmount(fields.get('initialDeposit'), { allowZero: true })) return showError('auth-error', 'Enter a non-negative initial deposit with at most two decimal places.');
    body.initialDeposit = Number(fields.get('initialDeposit'));
  }
  setBusy(true); showError('auth-error', ''); $('auth-submit').textContent = register ? 'Creating account…' : 'Signing in…';
  try {
    const data = await (register ? api.register(body) : api.login(body));
    if (!data.accessToken || !data.userId) throw new ApiError('The backend did not return an access token and user ID.');
    state.epoch += 1; api.token = data.accessToken; state.mode = 'live';
    state.profile = { id: data.userId, firstName: data.firstName, lastName: data.lastName, email: data.email, walletBalance: data.walletBalance };
    state.stocks = []; state.portfolio = null; state.orders = []; state.history.clear();
    state.portfolioAvailable = false; state.profileAvailable = true; state.stocksAvailable = false; state.portfolioPending = null; state.lastUpdate = null;
    $('auth-dialog').close(); event.target.elements.password.value = ''; state.refreshing = false;
    await refresh({ manual: true });
  } catch (error) { showError('auth-error', error.message); }
  finally { setBusy(false); $('auth-submit').textContent = register ? 'Create account' : 'Sign in'; render(); }
});

$('add-funds-button').addEventListener('click', () => {
  $('funds-description').textContent = state.mode === 'demo' ? 'Add practice funds to your demo wallet.' : 'Add simulation funds to your platform wallet.';
  showError('funds-error', ''); $('funds-submit').textContent = 'Add funds'; openDialog('funds-dialog');
});
$('funds-form').addEventListener('submit', async event => {
  event.preventDefault(); if (state.pending) return;
  const amount = new FormData(event.target).get('amount');
  if (!validAmount(amount)) return showError('funds-error', 'Enter a positive amount with at most two decimal places.');
  setBusy(true); showError('funds-error', ''); $('funds-submit').textContent = 'Adding funds…';
  let ambiguousFailure = false;
  try {
    if (state.mode === 'demo') { demo.addFunds(amount); syncDemo(); }
    else {
      const data = await api.addFunds(state.profile.id, Number(amount).toFixed(2));
      if (!data.id || !Number.isFinite(Number(data.walletBalance))) throw new ApiError('Deposit response could not be read. Refresh your wallet before retrying.');
      state.profile = data; state.profileAvailable = true;
    }
    $('funds-dialog').close(); toast(`${money(amount)} added to ${state.mode === 'demo' ? 'your demo wallet' : 'your wallet'}.`);
  } catch (error) {
    showError('funds-error', error.message);
    ambiguousFailure = !error.status || error.status >= 500 || (error.status >= 200 && error.status < 300);
    if (error.status === 401) clearLiveSession('Your session expired. Sign in again.');
  } finally {
    setBusy(false); render();
    $('funds-submit').disabled = ambiguousFailure;
    $('funds-submit').textContent = ambiguousFailure ? 'Close and refresh wallet before retrying' : 'Add funds';
  }
});

$('order-form').addEventListener('submit', event => {
  event.preventDefault(); const quantity = Number($('order-quantity').value);
  const error = validate(quantity); if (error) return toast(error);
  const stock = selectedStock();
  state.review = { symbol: state.symbol, orderType: state.side, quantity, price: Number(stock.price) };
  $('review-content').innerHTML = `<dl class="detail-list"><div><dt>Side / stock</dt><dd>${esc(state.side)} ${esc(state.symbol)}</dd></div><div><dt>Quantity</dt><dd>${quantity.toLocaleString('en-IN')} shares</dd></div><div><dt>Estimated price</dt><dd>${money(stock.price)}</dd></div><div><dt>Estimated total</dt><dd>${money(Number(stock.price) * quantity)}</dd></div></dl>`;
  showError('review-error', ''); $('confirm-order-button').textContent = `Confirm ${state.side.toLowerCase()}`;
  $('confirm-order-button').disabled = false; $('confirm-order-button').className = `button button-primary ${state.side === 'SELL' ? 'sell' : ''}`;
  openDialog('review-dialog');
});

$('confirm-order-button').addEventListener('click', async () => {
  if (state.pending || !state.review) return;
  const { symbol, orderType, quantity } = state.review;
  setBusy(true); showError('review-error', ''); $('confirm-order-button').textContent = 'Submitting order…';
  let submissionStarted = false, ambiguousFailure = false;
  try {
    let wallet = state.profile.walletBalance, portfolio = state.portfolio, stock = selectedStock();
    if (state.mode === 'live') {
      // Recheck funds, the current quote and ownership immediately before submitting.
      const [profileData, stockData, portfolioData] = await Promise.all([api.profile(), api.quote(symbol), api.portfolio()]);
      state.profile = profileData; state.portfolio = portfolioData;
      state.profileAvailable = true; state.portfolioAvailable = true;
      wallet = profileData.walletBalance; portfolio = portfolioData; stock = stockData;
    }
    const error = orderValidation({ stock, quantity, side: orderType, wallet, holdings: portfolio?.holdings || [], portfolioAvailable: Boolean(portfolio && Array.isArray(portfolio.holdings)) });
    if (error) throw new Error(error);
    const oldQuantity = Number(portfolio?.holdings?.find(item => item.symbol === symbol)?.quantity || 0);
    submissionStarted = true;
    const order = state.mode === 'demo' ? demo.placeOrder({ symbol, orderType, quantity }) : await api.placeOrder({ symbol, orderType, quantity });
    if (!order.id || !order.orderStatus) throw new ApiError('The order response could not be read. Refresh order history before retrying.');
    if (state.mode === 'demo') syncDemo();
    else {
      state.orders = [order, ...state.orders.filter(item => item.id !== order.id)];
      if (order.orderStatus === 'EXECUTED') state.portfolioPending = { symbol, quantity: oldQuantity + (orderType === 'BUY' ? quantity : -quantity) };
    }
    $('review-dialog').close(); showOrderDetails(order);
    toast(order.orderStatus === 'EXECUTED' ? `${orderType === 'BUY' ? 'Bought' : 'Sold'} ${quantity} ${symbol} at ${money(order.price)}.` : `Order ${statuses[order.orderStatus]?.toLowerCase() || order.orderStatus.toLowerCase()}. See the order details.`);
    if (state.mode === 'live') {
      state.refreshing = false; await refresh({ manual: true });
      const epoch = state.epoch;
      setTimeout(() => { if (epoch === state.epoch && state.mode === 'live') void refresh(); }, 2200);
    }
  } catch (error) {
    showError('review-error', error.message);
    ambiguousFailure = submissionStarted && (!error.status || error.status >= 500 || (error.status >= 200 && error.status < 300));
    if (error.status === 401) clearLiveSession('Your session expired. Sign in again.');
  } finally {
    setBusy(false); render();
    $('confirm-order-button').disabled = ambiguousFailure;
    $('confirm-order-button').textContent = ambiguousFailure ? 'Close and refresh orders before retrying' : `Confirm ${orderType.toLowerCase()}`;
  }
});

function showOrderDetails(order) {
  const flagged = order.flaggedByAI === true || order.orderStatus === 'FLAGGED';
  const aiText = flagged ? order.aiReason || order.failureReason || 'The backend flagged this order.' : 'No AI flag recorded. This response does not confirm that AI screening completed.';
  $('details-content').innerHTML = `<p>${statusBadge(order.orderStatus)} <span class="side-badge ${order.orderType === 'SELL' ? 'sell' : 'buy'}">${esc(order.orderType)} ${esc(order.symbol)}</span></p><p class="detail-id">Order ${esc(order.id)}</p><dl class="detail-list"><div><dt>Quantity</dt><dd>${Number(order.quantity).toLocaleString('en-IN')} shares</dd></div><div><dt>Price</dt><dd>${money(order.price)}</dd></div><div><dt>Total amount</dt><dd>${money(order.totalAmount)}</dd></div><div><dt>Created</dt><dd>${esc(dateTime(order.createdAt))}</dd></div><div><dt>Executed</dt><dd>${esc(dateTime(order.executedAt))}</dd></div></dl>${order.orderStatus === 'FAILED' ? `<p class="flag-description error">${esc(order.failureReason || 'The backend could not execute this order.')}</p>` : ''}<p class="flag-description ${flagged ? '' : 'neutral'}">${esc(aiText)}</p>${state.mode === 'demo' ? '<p class="modal-foot">Sample order. Demo mode does not call the AI service.</p>' : ''}`;
  $('details-dialog').showModal();
}
document.addEventListener('click', async event => {
  const button = event.target.closest('[data-order-id]'); if (!button || state.pending) return;
  const order = state.orders.find(item => item.id === button.dataset.orderId);
  if (!order) return;
  if (state.mode === 'demo') return showOrderDetails(order);
  const epoch = state.epoch;
  button.disabled = true;
  try {
    const latest = await api.order(order.id);
    if (epoch !== state.epoch) return;
    showOrderDetails(latest);
    state.orders = state.orders.map(item => item.id === latest.id ? latest : item); renderOrders();
  } catch (error) {
    if (epoch !== state.epoch) return;
    if (error.status === 401) clearLiveSession('Your session expired. Sign in again.');
    else toast(error.message);
  } finally { button.disabled = false; }
});

async function initialize() {
  render(); syncView();
  try {
    const response = await fetch('/frontend-config', { credentials: 'omit' });
    if (response.ok) {
      const config = await response.json(); state.gatewayUrl = config.gatewayUrl;
      state.pollIntervalMs = Math.max(2000, Number(config.pollIntervalMs) || 5000);
    }
  } catch { /* Default local gateway remains usable. */ }
  setInterval(() => {
    if (!state.paused && !state.pending && !document.hidden && !document.querySelector('dialog[open]')) void refresh();
  }, state.pollIntervalMs);
  document.addEventListener('visibilitychange', () => { if (!document.hidden && !state.paused && !state.pending) void refresh(); });
}
void initialize();
