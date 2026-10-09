import { emptyPortfolio } from './trading.js';

const round = amount => Math.round((Number(amount) + Number.EPSILON) * 100) / 100;

export class DemoAccount {
  constructor() {
    this.tick = 0;
    this.profile = { id: 'demo-trader', firstName: 'Demo', lastName: 'Trader', email: 'trader@example.com', walletBalance: 25000 };
    this.stocks = [
      ['RELIANCE', 'Reliance Industries', 2850, 2840, 2900, 2800, 1500000],
      ['TCS', 'Tata Consultancy Services', 3920, 3910, 3980, 3890, 800000],
      ['INFY', 'Infosys', 1650, 1645, 1680, 1630, 1200000],
      ['AAPL', 'Apple Inc', 189.50, 189, 191, 188, 5000000],
      ['GOOGL', 'Alphabet Inc', 141.80, 141.20, 143, 140.50, 3000000],
      ['MSFT', 'Microsoft Corp', 378.90, 378, 381, 377, 4000000]
    ].map(([symbol, companyName, price, open, high, low, volume]) => ({ symbol, companyName, price, open, high, low, volume, change: round(price - open), changePercent: round((price / open - 1) * 100), timestamp: new Date().toISOString() }));
    this.holdings = [{ symbol: 'RELIANCE', quantity: 12, averageBuyPrice: 2812, invested: 33744 }, { symbol: 'TCS', quantity: 5, averageBuyPrice: 3940, invested: 19700 }, { symbol: 'INFY', quantity: 10, averageBuyPrice: 1628, invested: 16280 }];
    const time = minutes => new Date(Date.now() - minutes * 60000).toISOString();
    this.orders = [
      { id: 'demo-flagged-01', userId: 'demo-trader', symbol: 'RELIANCE', orderType: 'BUY', quantity: 1200, price: 2850, totalAmount: 3420000, orderStatus: 'FLAGGED', flaggedByAI: true, aiReason: 'Illustrative demo flag: unusual order size.', createdAt: time(8), executedAt: null },
      { id: 'demo-executed-03', userId: 'demo-trader', symbol: 'INFY', orderType: 'BUY', quantity: 10, price: 1628, totalAmount: 16280, orderStatus: 'EXECUTED', flaggedByAI: false, createdAt: time(30), executedAt: time(30) },
      { id: 'demo-executed-02', userId: 'demo-trader', symbol: 'TCS', orderType: 'BUY', quantity: 5, price: 3940, totalAmount: 19700, orderStatus: 'EXECUTED', flaggedByAI: false, createdAt: time(70), executedAt: time(70) },
      { id: 'demo-executed-01', userId: 'demo-trader', symbol: 'RELIANCE', orderType: 'BUY', quantity: 12, price: 2812, totalAmount: 33744, orderStatus: 'EXECUTED', flaggedByAI: false, createdAt: time(110), executedAt: time(110) }
    ];
  }

  portfolio() {
    const result = emptyPortfolio();
    result.holdings = this.holdings.map(holding => {
      const currentPrice = this.stocks.find(stock => stock.symbol === holding.symbol).price;
      const currentValue = round(currentPrice * holding.quantity);
      const pnlPercent = holding.invested ? round((currentValue - holding.invested) / holding.invested * 100) : 0;
      result.currentValue += currentValue;
      result.totalInvested += holding.invested;
      return { ...holding, currentPrice, currentValue, pnlPercent, isProfit: currentValue > holding.invested };
    });
    result.currentValue = round(result.currentValue);
    result.totalInvested = round(result.totalInvested);
    result.totalPnl = round(result.currentValue - result.totalInvested);
    result.totalPnlPercent = result.totalInvested ? round(result.totalPnl / result.totalInvested * 100) : 0;
    return result;
  }

  updateQuotes() {
    this.tick += 1;
    for (const [i, stock] of this.stocks.entries()) {
      stock.price = round(stock.price * (1 + Math.sin(this.tick * .8 + i) * .0004));
      stock.high = Math.max(stock.high, stock.price);
      stock.low = Math.min(stock.low, stock.price);
      stock.change = round(stock.price - stock.open);
      stock.changePercent = round(stock.change / stock.open * 100);
      stock.timestamp = new Date().toISOString();
    }
  }

  history(symbol) {
    const stock = this.stocks.find(item => item.symbol === symbol);
    return Array.from({ length: 40 }, (_, i) => round(stock.open + (stock.price - stock.open) * i / 39 + (Math.sin(i * .9) + Math.cos(i * .41)) * stock.price * .0008));
  }

  addFunds(amount) { this.profile.walletBalance = round(this.profile.walletBalance + Number(amount)); }

  placeOrder({ symbol, orderType, quantity }) {
    const stock = this.stocks.find(item => item.symbol === symbol);
    const totalAmount = round(stock.price * quantity);
    const holding = this.holdings.find(item => item.symbol === symbol);
    if (orderType === 'BUY' && totalAmount > this.profile.walletBalance) throw new Error('Insufficient demo funds.');
    if (orderType === 'SELL' && (!holding || quantity > holding.quantity)) throw new Error('Insufficient demo shares.');
    if (orderType === 'BUY') {
      this.profile.walletBalance = round(this.profile.walletBalance - totalAmount);
      if (holding) {
        holding.quantity += quantity;
        holding.invested = round(holding.invested + totalAmount);
        holding.averageBuyPrice = round(holding.invested / holding.quantity);
      } else this.holdings.push({ symbol, quantity, averageBuyPrice: stock.price, invested: totalAmount });
    } else {
      this.profile.walletBalance = round(this.profile.walletBalance + totalAmount);
      holding.quantity -= quantity;
      holding.invested = round(holding.averageBuyPrice * holding.quantity);
      this.holdings = this.holdings.filter(item => item.quantity > 0);
    }
    const order = { id: `demo-${crypto.randomUUID()}`, userId: this.profile.id, symbol, orderType, quantity, price: stock.price, totalAmount, orderStatus: 'EXECUTED', flaggedByAI: false, createdAt: new Date().toISOString(), executedAt: new Date().toISOString() };
    this.orders.unshift(order);
    return order;
  }
}
