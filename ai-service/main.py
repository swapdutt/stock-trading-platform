from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional
import uvicorn
import logging
import anthropic
import json
from datetime import datetime

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Claude client
client = anthropic.Anthropic(
    api_key="YOUR_ANTHROPIC_API_KEY"  # ← replace with your key
)

app = FastAPI(
    title="AI Fraud Detection Service — Powered by Claude",
    description="""
    Real AI fraud detection using Claude (Anthropic).
    
    Claude analyzes each trade and returns:
    - Fraud score (0.0 to 1.0)
    - Whether order is suspicious
    - Detailed reasoning
    
    This is REAL AI — not hardcoded rules.
    """,
    version="2.0.0"
)


class OrderData(BaseModel):
    orderId: str
    userId: str
    symbol: str
    type: str
    quantity: int
    totalAmount: float
    price: float
    recentOrderCount: Optional[int] = 0


class FraudCheckResult(BaseModel):
    orderId: str
    isSuspicious: bool
    fraudScore: float
    reason: Optional[str] = None
    recommendation: str
    aiProvider: str = "Claude (Anthropic)"
    checkedAt: str


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "ai-fraud-detection",
        "aiProvider": "Claude (Anthropic)"
    }


@app.post("/api/fraud/check", response_model=FraudCheckResult)
def check_fraud(order: OrderData):
    """
    Real AI Fraud Detection using Claude.

    Sends order details to Claude API.
    Claude analyzes and returns fraud assessment.
    No hardcoded rules — actual AI intelligence.
    """
    logger.info(
        f"Claude fraud check — orderId: {order.orderId} "
        f"symbol: {order.symbol} "
        f"type: {order.type} "
        f"quantity: {order.quantity} "
        f"amount: {order.totalAmount}"
    )

    try:
        # Build prompt for Claude
        prompt = f"""You are an AI fraud detection system for a stock trading platform.

Analyze the following trade order and determine if it is suspicious:

Order Details:
- Order ID: {order.orderId}
- User ID: {order.userId}
- Stock Symbol: {order.symbol}
- Order Type: {order.type}
- Quantity: {order.quantity} shares
- Price per share: ₹{order.price}
- Total Amount: ₹{order.totalAmount}
- Recent orders in last hour: {order.recentOrderCount}

Analyze this trade for:
1. High frequency trading (too many orders in short time)
2. Unusually large order amounts
3. Suspicious quantity patterns
4. Any other red flags

Respond ONLY with a valid JSON object in this exact format:
{{
    "isSuspicious": true or false,
    "fraudScore": number between 0.0 and 1.0,
    "reason": "brief explanation if suspicious, null if clean",
    "recommendation": "BLOCK or ALLOW or MONITOR"
}}

Be reasonable — legitimate large trades by genuine investors should not be flagged.
Only flag genuinely suspicious patterns."""

        # Call Claude API
        message = client.messages.create(
            model="claude-sonnet-4-6",
            max_tokens=256,
            messages=[
                {
                    "role": "user",
                    "content": prompt
                }
            ]
        )

        # Parse Claude's response
        response_text = message.content[0].text.strip()
        logger.info(f"Claude response: {response_text}")

        # Parse JSON response
        ai_result = json.loads(response_text)

        is_suspicious = ai_result.get("isSuspicious", False)
        fraud_score = float(ai_result.get("fraudScore", 0.0))
        reason = ai_result.get("reason", None)
        recommendation = ai_result.get(
            "recommendation", "ALLOW")

        if is_suspicious:
            logger.warning(
                f"ORDER FLAGGED by Claude — "
                f"orderId: {order.orderId} "
                f"score: {fraud_score} "
                f"reason: {reason}"
            )
        else:
            logger.info(
                f"Order cleared by Claude — "
                f"orderId: {order.orderId} "
                f"score: {fraud_score}"
            )

        return FraudCheckResult(
            orderId=order.orderId,
            isSuspicious=is_suspicious,
            fraudScore=round(fraud_score, 2),
            reason=reason,
            recommendation=recommendation,
            aiProvider="Claude (Anthropic)",
            checkedAt=datetime.now().isoformat()
        )

    except json.JSONDecodeError as e:
        logger.error(f"Failed to parse Claude response: {e}")
        # Fallback to allow if parsing fails
        return FraudCheckResult(
            orderId=order.orderId,
            isSuspicious=False,
            fraudScore=0.0,
            reason="AI parsing error — defaulting to allow",
            recommendation="ALLOW",
            aiProvider="Claude (Anthropic) — fallback",
            checkedAt=datetime.now().isoformat()
        )

    except Exception as e:
        logger.error(f"Claude API error: {e}")
        # Fallback — don't block orders if AI is down
        return FraudCheckResult(
            orderId=order.orderId,
            isSuspicious=False,
            fraudScore=0.0,
            reason=f"AI service error — defaulting to allow",
            recommendation="ALLOW",
            aiProvider="Claude (Anthropic) — unavailable",
            checkedAt=datetime.now().isoformat()
        )


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8086,
        reload=True
    )
