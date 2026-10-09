# StockDesk — simple trading UI - Built by ChatGPT

A responsive UI for [swapdutt/stock-trading-platform](https://github.com/swapdutt/stock-trading-platform), built with HTML, CSS, JavaScript, and a small Node.js server. No npm dependencies or build step.

## Run locally

Extract the `frontend` folder into the repository root, alongside the Java service folders. Install Node.js 18 or newer, then run:

```bash
cd stock-trading-platform/frontend
npm start
```

Open **http://localhost:5173**. No `npm install` is needed.

The UI opens in **Demo mode**. Try buying and selling sample stocks, adding practice funds, searching markets, and viewing your portfolio and orders. Demo data resets on page reload and never calls the backend.

## Use the backend

1. Start the repository's infrastructure and Java services.
2. Keep the API gateway running at `http://localhost:8080`.
3. Click **Connect backend**, then sign in or choose **Create account**.

The browser calls the frontend server, which forwards supported requests to the gateway. JWTs travel in the `Authorization` header; the gateway supplies `X-User-Id`. This avoids browser CORS changes. Tokens remain in memory, so a reload requires signing in again.

Change the gateway or UI port on macOS/Linux:

```bash
GATEWAY_URL=http://127.0.0.1:8080 PORT=5173 npm start
```

PowerShell:

```powershell
$env:GATEWAY_URL = "http://127.0.0.1:8080"
$env:PORT = "5173"
npm start
```

The server binds to `127.0.0.1` by default. Configuration comes from environment variables; `.env` files are not automatically loaded.

## Features

- Overview with wallet balance, portfolio value, invested capital, and returns.
- Searchable market watch and a selected-stock price chart.
- Buy/sell order ticket with quantity checks and order confirmation.
- Portfolio holdings, order history, and failed or AI-flagged order details.
- Registration, login, logout, and add funds.
- Automatic refresh every five seconds, manual refresh, and pause/resume.
- Responsive layout, keyboard navigation, loading states, and backend errors.

Demo chart data is illustrative. Connected charts show quotes observed in this browser session. All values use the backend simulation's INR convention. Market updates use REST polling.

## API mapping

| UI action | Gateway request |
| --- | --- |
| Register | `POST /api/v1/users/register` |
| Sign in | `POST /api/v1/users/login` |
| Profile/wallet | `GET /api/v1/users/userDetail` |
| Add funds | `POST /api/v1/users/{userId}/funds/add?amount=5000.00` |
| Stock list | `GET /api/v1/market/stocks` |
| Quote | `GET /api/v1/market/stocks/{symbol}` |
| Place order | `POST /api/v1/orders/` |
| Order history | `GET /api/v1/orders/current-order-list` |
| Order details | `GET /api/v1/orders/{orderId}` |
| Portfolio | `GET /api/v1/portfolio/current-portfolio` |

Order requests contain `symbol`, `orderType` (`BUY` or `SELL`), and integer `quantity`. The trailing slash on order submission matches the controller. Portfolio updates can take a few seconds after execution because the backend uses Kafka.

Writes are not automatically retried. After a timeout, check orders or wallet funds before submitting again. Backend failures stay visible and never silently become demo results.

## Verification

```bash
npm run check
npm test
```

All JavaScript syntax checks and six tests pass. Tests cover exact API routes, request bodies, JWT forwarding, gateway errors, input checks, demo wallet/holding updates, and HTML escaping. The Java services were not started here. Browser preview was unavailable in this environment; confirm the layout and connected workflow locally.

For a local smoke test, register, add funds, buy one share, wait for the holding, sell it, and inspect the order history.
