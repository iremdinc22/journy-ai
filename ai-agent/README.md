# Journy AI Agent

Python FastAPI service for Journy's travel agent reasoning layer.

The service receives the authenticated trip/day context from the Spring Boot backend and returns a structured action preview. It does not write to the database. Spring Boot remains the owner of auth, trip data and applying confirmed itinerary changes.

## Run locally

```bash
cd ai-agent
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --port 8001
```

`OPENAI_API_KEY` is optional. If it is not set, the service uses deterministic fallback logic with the same response schema.

## Endpoint

```http
POST /v1/agent/message
```

Returns:

- intent
- assistant message
- action preview
- affected stops
- route impact
- explainability reasons
- confirmation requirement
