<div align="center">
  <img src="./mobile/assets/images/journy-logo.png" alt="Journy logo" width="96" />

  <h1>Journy</h1>

  <p><strong>AI travel planning for calmer, smarter city trips.</strong></p>

  <p>
    Journy builds personalized city itineraries, learns how each traveler moves,
    and adapts the plan with AI before and during the trip.
  </p>

  <p>
    <img alt="Expo" src="https://img.shields.io/badge/Expo-Mobile-111827?style=flat-square&logo=expo&logoColor=white" />
    <img alt="React Native" src="https://img.shields.io/badge/React_Native-TypeScript-61DAFB?style=flat-square&logo=react&logoColor=111827" />
    <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-API-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
    <img alt="FastAPI" src="https://img.shields.io/badge/FastAPI-AI_Agent-009688?style=flat-square&logo=fastapi&logoColor=white" />
    <img alt="OpenAI" src="https://img.shields.io/badge/OpenAI-Agent-412991?style=flat-square&logo=openai&logoColor=white" />
    <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-Database-4169E1?style=flat-square&logo=postgresql&logoColor=white" />
    <img alt="Open-Meteo" src="https://img.shields.io/badge/Open--Meteo-Weather-38BDF8?style=flat-square" />
  </p>
</div>

---

## Overview

Journy is a mobile-first AI travel planner. It turns a destination, dates, pace,
budget, interests and starting area into a practical day-by-day city plan.

The product direction is:

```txt
Before trip
  Trip setup -> AI plan generation -> Explore -> Saved places

During trip
  Today mode -> route actions -> weather adjustment -> AI itinerary changes

After trip
  Saved plans -> favorites -> travel taste profile
```

Journy is designed to feel like a calm personal city companion rather than a
crowded travel blog or a generic chatbot.

---

## Core Product Features

| Area | Current experience |
| --- | --- |
| Trip setup | Destination, dates, travelers, budget, pace, interests and route starting area |
| AI plan preview | Live setup preview that changes as the user selects preferences |
| Itinerary | Day cards with schedule times, travel-time rows, walking distance and editable stops |
| Today mode | Home adapts to the trip lifecycle and highlights the active day |
| Explore | City-aware local picks connected back into the current itinerary |
| Place detail | Best-fit day suggestion, route-fit reasons and add-to-day flow |
| Map detail | Route/place toggle, numbered markers, selected stop card and route actions |
| Journy AI | Context-aware quick actions and chat with preview-before-apply behavior |
| Weather | Open-Meteo powered weather signal with itinerary adjustment preview |
| Profile | Current trip, travel identity, taste levels, saved plans and favorites |
| Saved places | Favorites plus collection-style saved place management |
| Localization | English and Turkish app language support |
| Theme | Light and dark mode with product-level mobile UI styling |

---

## Architecture

Journy is split into three services:

```txt
mobile/      Expo React Native app
backend/     Spring Boot API, auth, trips, itinerary and persistence
ai-agent/    FastAPI reasoning service for agent previews
```

Runtime flow:

```txt
Mobile app
  -> Spring Boot API
  -> PostgreSQL
  -> Python AI Agent
  -> OpenAI when configured
  -> deterministic fallback when AI key/service is unavailable
```

The Spring Boot backend owns authentication, persisted trip data, itinerary
updates and apply operations. The Python agent receives context and returns
structured previews; it does not directly write to the database.

---

## AI Agent Layer

Journy's AI layer is built around confirmation-first plan changes. The assistant
can reason over:

- current trip destination, dates, budget, pace and interests
- active itinerary day
- stop density and walking distance
- food/coffee windows
- weather-sensitive outdoor stops
- language preference
- preview impact before applying a change

Supported agent behaviors include:

| Agent behavior | Example |
| --- | --- |
| Lighter day | "I am tired" or "Make today lighter" |
| Food break | "Find lunch nearby" or "Add a food stop" |
| Indoor/weather plan | "Make an indoor plan for today" |
| Route fit explanation | Before/after stop count, walking impact and reasons |
| Apply flow | User sees a preview, then confirms before the itinerary changes |

The agent request includes `language`, so Turkish users can receive localized AI
responses instead of relying only on frontend translation fallbacks.

---

## Itinerary Engine

The itinerary experience is moving toward a real daily schedule, not just a list
of places.

Current planning behavior includes:

- day-by-day route structure
- themed daily titles and short summaries
- schedule windows such as `09:30 - 10:35`
- travel-time timeline rows such as `10:35 - 10:47 - 12 min walk`
- stop actions: mark optional, move earlier/later, move to another day, remove
- weather adjustment preview and apply flow
- starting-area-aware setup preview
- city fallback logic for destinations not yet in the seed database

---

## Data And Providers

Journy currently combines seeded/local data, dynamic fallback generation and
external provider signals.

| Data type | Current source |
| --- | --- |
| Trips, users, saved places | PostgreSQL through Spring Boot |
| AI reasoning | Python FastAPI agent, OpenAI optional |
| Weather | Open-Meteo |
| City and place visuals | Local curated mappings plus dynamic fallbacks |
| Places | Seed/starter data and backend endpoints |

The next major product step is a full live place provider abstraction such as
Google Places, Foursquare or OpenStreetMap/Nominatim/Overpass so any city can
return real venues, coordinates, photos, opening hours and ratings.

---

## Screens

| Screen | Purpose |
| --- | --- |
| Welcome | Product entry, auth prompt and first impression |
| Login/Register | JWT-backed authentication and guest-friendly entry points |
| Home | Current trip overview and Today mode |
| Trip Setup | Preference collection and live AI plan preview |
| Loading Plan | AI plan creation progress and failure handling |
| Plan | Day-by-day itinerary, schedule, weather and stop management |
| Day Route Detail | Route map, markers, stop cards and route actions |
| Explore | City-aware discovery feed by category |
| Place Detail | Add-to-day and best-fit route explanation |
| AI Assistant | Context-aware assistant actions and chat |
| Profile | Travel identity, taste profile, saved plans and favorites |
| Saved Places | Favorites and collections |
| Saved Plans | Saved trip plans |
| Settings | Language, dark mode and travel preferences |

---

## Tech Stack

| Layer | Stack |
| --- | --- |
| Mobile | Expo, React Native, TypeScript, React Navigation, React Native Maps |
| Backend | Java, Spring Boot, Spring Security, JPA |
| AI Agent | Python, FastAPI, OpenAI API, deterministic fallback agents |
| Weather | Open-Meteo |
| Database | PostgreSQL |
| Auth | JWT access token and refresh token |
| Docs | Swagger / OpenAPI |

---

## Run Locally

### One-command dev startup

The easiest way to start the full development stack is:

```bash
cd /Users/iremdinc/Journy
./scripts/dev.sh
```

This script starts services in order:

1. Docker dependencies from `backend/compose.yaml`
2. Spring Boot backend on `8080`
3. FastAPI AI agent on `8001`
4. Expo in LAN mode with QR code support

Expo remains interactive, so you can press:

```txt
i  open iOS simulator
a  open Android
r  reload app
```

Logs are written to:

```txt
.dev-logs/backend.log
.dev-logs/ai-agent.log
```

### Manual startup

Backend:

```bash
cd backend
docker compose up -d
./mvnw spring-boot:run
```

AI Agent:

```bash
cd ai-agent
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --port 8001
```

Mobile:

```bash
cd mobile
npm install
npx expo start -c --lan
```

Useful local URLs:

```txt
Backend: http://localhost:8080
Swagger: http://localhost:8080/swagger-ui.html
AI Agent: http://localhost:8001
AI Agent health: http://localhost:8001/health
```

Demo account:

```txt
admin@journy.app
admin123
```

---

## Environment

AI is optional in local development:

```txt
OPENAI_API_KEY=your_key_here
OPENAI_MODEL=gpt-4.1-mini
```

If `OPENAI_API_KEY` is not configured, the Python service still returns
deterministic fallback previews with the same response shape.

Weather uses Open-Meteo, which does not require an API key for the current
development integration.

The mobile API client can discover the Expo/Metro runtime host so local IP
changes do not require hardcoding a new backend IP each time.

---

## Quality Notes

Journy includes product states that are important for a real mobile app:

- loading, empty, error and offline-style fallback states
- backend retry cards
- preview data when live data cannot be loaded
- localized Turkish/English UI strings
- dark mode and light mode theme support
- confirmation-first destructive actions
- route changes shown before they are applied

---

## Roadmap

Next high-impact product steps:

- Live place provider abstraction for real venues, photos, opening hours and coordinates
- Constraint validator for opening hours, visit duration, travel time and meal windows
- More complete route optimization using real map/directions data
- Offline saved itinerary support
- Stronger post-trip history and preference learning
- Group trip collaboration
- Production deployment, observability and error reporting

---

## Vision

Journy's long-term vision is:

> Journy builds your trip, learns how you travel and continuously adapts the
> itinerary while you are there.

In practice, a user should be able to say:

```txt
I am going to Paris for four days.
```

Journy should handle the rest: realistic daily routes, local food, museums,
neighborhoods, walking pace, weather changes, budget fit and flexible AI-powered
adjustments during the trip.
