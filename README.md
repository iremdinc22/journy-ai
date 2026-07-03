<div align="center">
  <img src="./mobile/assets/images/journy-logo.png" alt="Journy logo" width="96" />

  <h1>Journy</h1>

  <p><strong>AI travel planning for calmer, smarter city trips.</strong></p>

  <p>
    A mobile travel assistant that creates personalized city plans from a destination,
    travel dates, budget, pace, and interests.
  </p>

  <p>
    <img alt="Expo" src="https://img.shields.io/badge/Expo-Mobile-111827?style=flat-square&logo=expo&logoColor=white" />
    <img alt="React Native" src="https://img.shields.io/badge/React_Native-TypeScript-61DAFB?style=flat-square&logo=react&logoColor=111827" />
    <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-API-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
    <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-Database-4169E1?style=flat-square&logo=postgresql&logoColor=white" />
  </p>
</div>

---

## At A Glance

Journy helps travelers stop planning across ten different apps. It brings trip setup, daily itinerary planning, local recommendations, and an AI assistant into one calm mobile experience.

| User gives Journy | Journy creates |
| --- | --- |
| Destination | Personalized city plan |
| Travel dates | Day-by-day itinerary |
| Budget | Food and activity fit |
| Pace | Realistic daily rhythm |
| Interests | Local picks and route ideas |

---

## The Problem

Travel planning is scattered.

Before a trip, users usually check blogs, Google reviews, maps, TikTok videos, Instagram saves, restaurant lists, museum guides, and weather apps. Even after all that, the plan can still feel messy: too many places in one day, long distances between stops, or recommendations that do not match the user's style.

Journy is built around one question:

> How can planning a city trip feel as simple as talking to a travel companion?

---

## What Journy Does

| Area | Experience |
| --- | --- |
| Trip setup | Destination, dates, traveler type, budget, pace, and interests |
| Smart itinerary | Days grouped by distance, rhythm, and realistic walking flow |
| Local discovery | Food, coffee, culture, neighborhoods, free activities, and hidden spots |
| AI assistant | Ask for lighter days, dinner ideas, nearby coffee, or rain-friendly plans |
| Profile | Saved plans, taste profile, current trip, and preference learning |

---

## User Journey

```txt
Start
  -> Choose destination
  -> Select dates
  -> Pick interests and budget
  -> Generate itinerary
  -> Explore local picks
  -> Ask AI to adjust the plan
```

Example:

```txt
Amsterdam
Oct 10 - Oct 14, 2026
Couple trip
Balanced budget
Coffee, museums, local food, walking
```

Journy can turn that into:

- walkable daily routes
- museum and culture windows
- local cafe breaks
- dinner zones near the final stop
- flexible afternoons
- balanced pacing across the trip

---

## Screens In The App

| Screen | Purpose |
| --- | --- |
| Welcome | Introduces the product and guides users into the app |
| Auth | Login, register, and guest access |
| Home | A simple starting point for the current trip |
| Trip Setup | Collects destination, dates, interests, budget, and pace |
| Plan | Shows day-by-day itinerary structure |
| Explore | Lists local recommendations by category |
| AI Assistant | Lets users ask for changes and nearby suggestions |
| Profile | Shows travel preferences, saved plans, and current trip |
| Settings | Handles app preferences and notification choices |

---

## Product Direction

Journy is designed to feel:

| Calm | Personal | Practical |
| --- | --- | --- |
| Soft colors and clean layouts | Plans shaped around user interests | Routes that make sense during real travel |

The goal is a premium but approachable travel product: less like a crowded travel blog, more like a personal city planning companion.

---

## Current Status

The project currently includes:

- mobile app screens and navigation
- onboarding, auth, setup, plan, explore, assistant, profile, settings, and notification flows
- backend API foundation
- JWT authentication
- trip creation
- itinerary generation foundation
- explore recommendation endpoints
- AI assistant endpoint foundation
- PostgreSQL setup
- Swagger API documentation

---

## Tech Overview

```txt
Journy/
  mobile/    Expo React Native app
  backend/   Spring Boot API
```

| Layer | Stack |
| --- | --- |
| Mobile | Expo, React Native, TypeScript, React Navigation |
| Backend | Java, Spring Boot, Spring Security, JPA |
| Database | PostgreSQL |
| Auth | JWT access token and refresh token |
| Docs | Swagger / OpenAPI |

---

## Run Locally

Backend:

```bash
cd backend
docker compose up -d
./mvnw spring-boot:run
```

Mobile:

```bash
cd mobile
npm install
npm start
```

Useful local URLs:

```txt
Backend: http://localhost:8080
Swagger: http://localhost:8080/swagger-ui.html
```

Demo account:

```txt
admin@journy.app
admin123
```

---

## Roadmap

- Connect mobile screens to the backend API
- Add real AI-powered itinerary generation
- Expand city data for Paris, Amsterdam, Rome, Barcelona, and more
- Add map-based route visualization
- Add hotel-aware recommendations
- Add weather-based replanning
- Add group trip planning
- Improve saved plans and preference learning

---

## Vision

Journy aims to become a personal AI travel companion that understands how each user likes to move through a city.

In the long term, a user should be able to say:

```txt
I am going to Paris for four days.
```

And Journy should plan the rest: daily routes, local food, museums, neighborhoods, walking pace, budget-friendly options, and flexible changes during the trip.
