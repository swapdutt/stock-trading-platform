# AI Fraud Detection Service

Python FastAPI service for real time trading fraud detection.

## How to run - MacOS

```bash
# Install dependencies
pip3 install -r requirements.txt

# Run the service
python3 main.py

# Or with uvicorn directly
uvicorn main:app --host 0.0.0.0 --port 8086 --reload
```

## API Documentation

Once running — open: http://localhost:8086/docs

## Fraud Detection Rules

| Rule | Threshold | Score Added |
|---|---|---|
| High frequency trading | > 10 orders/hour | 0.1 - 0.4 |
| Large order amount | > ₹5,00,000 | 0.3 |
| Large quantity | > 1000 shares | 0.2 |
| Round number quantity | Multiple of 100, >= 500 | 0.1 |

**Fraud Score > 0.5 → Order Blocked**

## Production Extension

Replace rule-based logic with ML model:

```python
# Example with scikit-learn
from sklearn.ensemble import RandomForestClassifier
import joblib

model = joblib.load('fraud_model.pkl')
features = extract_features(order)
fraud_score = model.predict_proba(features)[0][1]
```
