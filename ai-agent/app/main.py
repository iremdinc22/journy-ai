from fastapi import FastAPI

from app.agents.travel_agent import TravelAgent
from app.schemas.agent import AgentMessageRequest, AgentMessageResponse

app = FastAPI(title="Journy AI Agent", version="0.1.0")
travel_agent = TravelAgent()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/agent/message", response_model=AgentMessageResponse)
def agent_message(request: AgentMessageRequest) -> AgentMessageResponse:
    return travel_agent.decide(request)
