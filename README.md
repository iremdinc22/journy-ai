# Journy

**AI-powered travel planning for calmer, smarter city trips.**

Journy is a mobile travel assistant designed to make trip planning feel simple, personal, and effortless. Instead of switching between blogs, maps, review apps, social media videos, and restaurant lists, users can plan their city trip from one place.

The user tells Journy where they are going, when they will travel, their budget, travel pace, and interests. Journy then creates a personalized city plan with realistic daily routes, local recommendations, food stops, cultural places, and flexible travel suggestions.

---

## The Idea

Planning a trip can quickly become overwhelming.

Before visiting a city, travelers often need to answer questions like:

- Which neighborhood should I explore first?
- What places are close to each other?
- Where should I eat something local?
- How can I avoid planning too much in one day?
- Which cafes, museums, markets, or local areas are actually worth visiting?
- How can I make my route more walkable?
- What should I do if the weather changes?

Journy brings these decisions into a single assistant-led experience.

The goal is not only to recommend places. The goal is to understand the traveler and create a plan that feels realistic, personal, and easy to follow.

---

## What Journy Helps With

Journy is built around a few core travel needs:

### Personalized Trip Planning

Every user can experience a city differently. A traveler interested in museums and coffee should not receive the same itinerary as someone focused on nightlife, shopping, or local food.

Journy adapts the trip plan based on:

- Destination
- Travel dates
- Budget
- Travel pace
- Traveler type
- Interests
- Local experience preferences

### Smart Daily Routes

Journy groups nearby places into the same day so users do not waste time moving across the city unnecessarily.

For example, if several landmarks, cafes, and restaurants are close to each other, they can be planned together as one walkable city day.

### Local Experiences

The app is designed to go beyond the most obvious tourist spots. It can highlight:

- Neighborhood cafes
- Local restaurants
- Food stops
- Museums and culture areas
- Markets
- Walking routes
- Free activities
- Calm breaks between plans

### AI Travel Assistant

Journy also includes an assistant experience for questions during the trip, such as:

- Find coffee nearby.
- Make today lighter.
- Suggest dinner near the last stop.
- Rebuild the plan if it rains.
- What should I do next?

---

## Main User Flow

```txt
Open Journy
  -> Sign in or continue as guest
  -> Choose destination
  -> Select travel dates
  -> Pick traveler type
  -> Choose interests
  -> Set budget and pace
  -> Generate personalized itinerary
  -> Explore daily routes and local picks
  -> Ask the AI assistant for changes during the trip
```

---

## Example Scenario

A user is planning a 4-day Amsterdam trip.

They choose:

```txt
Destination: Amsterdam
Dates: Oct 10 - Oct 14, 2026
Traveler type: Couple
Budget: Balanced
Pace: Balanced
Interests: Coffee, Museums, Local Food, Walking
```

Journy can create a plan with:

- A calm first day around museums and canals
- Local coffee stops between activities
- Dinner areas near the final stop of the day
- Flexible afternoons instead of overloaded schedules
- Walkable routes grouped by distance
- Food and culture recommendations based on the user's style

---

## App Sections

### Welcome

A polished entry screen that introduces Journy as a calm AI travel planner and guides users toward account creation, sign in, or guest mode.

### Home

A simple starting point for the user’s current travel plan, destination context, and next actions.

### Trip Setup

The planning form where users choose destination, dates, traveler type, interests, budget, and preferred pace.

### Plan

The itinerary area where days are organized into realistic routes with stops, walking rhythm, and trip structure.

### Explore

A recommendation space for food, culture, coffee, free activities, and local places.

### AI Assistant

A chat-style screen where users can ask for route changes, nearby ideas, lighter days, dinner suggestions, or weather-aware updates.

### Profile

A personal travel preference area showing current trip details, taste profile, saved plans, and travel style.

### Settings and Notifications

Supporting screens for app preferences, notification controls, and travel-related updates.

---

## Product Direction

Journy is designed to feel:

- Calm
- Premium
- Simple
- Personal
- Local-first
- Practical during real travel

The visual direction focuses on soft colors, clean spacing, mobile-first layouts, and a product experience that feels less like a travel blog and more like a personal planning companion.

---

## Current Project Status

The project currently includes:

- Mobile app screens and navigation
- Welcome, login, register, trip setup, itinerary, explore, assistant, profile, settings, and notification flows
- Backend API foundation
- Authentication system
- Trip creation flow
- Itinerary generation foundation
- Explore recommendation API
- AI assistant API foundation
- PostgreSQL database setup
- Swagger API documentation

The mobile interface and backend foundation are both prepared for future integration and expansion.

---

## Technology Overview

Journy is organized as a mobile app with a backend service.

```txt
Journy/
  mobile/    Mobile application
  backend/   Backend API
```

Main technologies:

- Expo
- React Native
- TypeScript
- Spring Boot
- PostgreSQL
- JWT Authentication

---

## Running the Project

### Backend

```bash
cd backend
docker compose up -d
./mvnw spring-boot:run
```

Backend URL:

```txt
http://localhost:8080
```

Swagger:

```txt
http://localhost:8080/swagger-ui.html
```

Demo account:

```txt
Email: admin@journy.app
Password: admin123
```

### Mobile

```bash
cd mobile
npm install
npm start
```

Then open the app through Expo on iOS or Android.

---

## Roadmap

Planned improvements:

- Connect all mobile screens to the backend API
- Add real AI-powered trip generation
- Expand city data for Paris, Amsterdam, Rome, Barcelona, and more
- Add map-based route visualization
- Add hotel-aware recommendations
- Add weather-based itinerary updates
- Add group trip planning
- Improve saved plans and user preference learning

---

## Long-Term Vision

Journy aims to become a personal AI travel companion that understands how each user likes to travel.

In the long term, a user should be able to say:

```txt
I'm going to Paris for four days.
```

And Journy should be able to plan the rest: daily routes, local food, museums, neighborhoods, walking pace, budget-friendly options, and flexible changes during the trip.

The goal is to remove the stressful part of travel planning and let users focus on the experience itself.
