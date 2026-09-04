import unittest
from app.agents.travel_agent import TravelAgent
from app.schemas.agent import AgentMessageRequest, AgentMessageResponse, AgentActionPreview, AgentIntent

class WeatherReliabilityTest(unittest.TestCase):
    def test_rain_request_without_forecast_never_offers_apply(self):
        agent=TravelAgent()
        agent.client=None
        for message in ['rain tomorrow', 'Make an indoor plan', 'yağmur']:
            response=agent.decide(AgentMessageRequest(message=message))
            self.assertEqual(response.intent,AgentIntent.GENERAL_GUIDANCE)
            self.assertFalse(response.preview.requiresConfirmation)
            self.assertIsNone(response.preview.minutesSaved)
            self.assertEqual(response.preview.affectedStops,[])
    def test_llm_rain_claim_is_replaced_with_unavailable_guidance(self):
        agent=TravelAgent()
        request=AgentMessageRequest(message='help me')
        response=AgentMessageResponse(message='Rain is expected',intent=AgentIntent.RAIN_REPLAN,preview=AgentActionPreview(intent=AgentIntent.RAIN_REPLAN,title='Rain',message='Rain',suggestedAction='Swap',minutesSaved=12,affectedStops=['Fake'],routeSummary='Rain',reasons=[],requiresConfirmation=True))
        result=agent._with_tool_preview(response,request,agent.context_analyzer.analyze_day(request.trip,request.day))
        self.assertNotIn('Rain is expected',result.message)
        self.assertFalse(result.preview.requiresConfirmation)
