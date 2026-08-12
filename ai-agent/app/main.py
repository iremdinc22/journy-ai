from fastapi import FastAPI, Request
from pydantic import ValidationError

from app.agents.travel_agent import TravelAgent
from app.schemas.agent import AgentMessageRequest, AgentMessageResponse

app = FastAPI(title="Journy AI Agent", version="0.1.0")
travel_agent = TravelAgent()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/agent/message", response_model=AgentMessageResponse)
async def agent_message(http_request: Request) -> AgentMessageResponse:
    try:
        payload = await http_request.json()
    except Exception:
        payload = {}
    if not isinstance(payload, dict):
        payload = {}
    try:
        request = AgentMessageRequest.model_validate(payload)
    except ValidationError as error:
        print({"agent_validation_error": error.errors(), "payload_keys": list(payload.keys())})
        request = AgentMessageRequest(message=str(payload.get("message") or ""))
    return travel_agent.decide(request)
