import test from 'node:test';
import assert from 'node:assert/strict';
import { orderValidation, validAmount, escapeHtml, getErrorMessage } from '../public/trading.js';
import { DemoAccount } from '../public/demo.js';

test('market order guard rejects overselling, fractional quantities and unavailable data', () => {
  const input = { stock: { symbol: 'TCS', price: 3920 }, quantity: 1, side: 'BUY', wallet: 10000, holdings: [] };
  assert.equal(orderValidation(input), '');
  assert.match(orderValidation({ ...input, quantity: 3 }), /funds/);
  assert.match(orderValidation({ ...input, quantity: 1.5 }), /whole-number/);
  assert.match(orderValidation({ ...input, quantity: 2147483648 }), /whole-number/);
  assert.match(orderValidation({ ...input, wallet: null }), /wallet/);
  assert.match(orderValidation({ ...input, stock: null }), /stock/);
  assert.match(orderValidation({ ...input, side: 'SELL' }), /hold 0/);
  assert.equal(orderValidation({ ...input, side: 'SELL', holdings: [{ symbol: 'TCS', quantity: 1 }] }), '');
  assert.match(orderValidation({ ...input, side: 'SELL', portfolioAvailable: false }), /holdings/);
});

test('amount validation prevents negative funds, excessive precision and non-finite values', () => {
  for (const value of ['-1', '0', '1.001', 'Infinity', '1e5', '', '9999999999999']) assert.equal(validAmount(value), false);
  for (const value of ['0.01', '1', '5000.00', '9999999999.99']) assert.equal(validAmount(value), true);
  assert.equal(validAmount('0', { allowZero: true }), true);
});

test('sample trades debit and credit exactly, preserve cost basis and remove a sold holding', () => {
  const demo = new DemoAccount();
  const wallet = demo.profile.walletBalance;
  const old = demo.holdings.find(item => item.symbol === 'AAPL');
  assert.equal(old, undefined);
  const buy = demo.placeOrder({ symbol: 'AAPL', orderType: 'BUY', quantity: 2 });
  assert.equal(buy.totalAmount, 379);
  assert.equal(demo.profile.walletBalance, wallet - 379);
  assert.equal(demo.portfolio().holdings.find(item => item.symbol === 'AAPL').quantity, 2);
  demo.placeOrder({ symbol: 'AAPL', orderType: 'SELL', quantity: 2 });
  assert.equal(demo.profile.walletBalance, wallet);
  assert.equal(demo.holdings.find(item => item.symbol === 'AAPL'), undefined);
  assert.throws(() => demo.placeOrder({ symbol: 'AAPL', orderType: 'SELL', quantity: 1 }), /Insufficient/);
  demo.addFunds('0.01'); demo.addFunds('0.02');
  assert.equal(demo.profile.walletBalance, wallet + .03);
});

test('backend strings are escaped and empty gateway unauthorized responses are understood', () => {
  assert.equal(escapeHtml('<img src=x onerror="x">'), '&lt;img src=x onerror=&quot;x&quot;&gt;');
  assert.match(getErrorMessage(null, 401), /session/);
  assert.equal(getErrorMessage({ errorMessage: 'Insufficient Wallet Balance' }, 404), 'Insufficient Wallet Balance');
});
