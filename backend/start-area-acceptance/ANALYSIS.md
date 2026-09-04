# Analysis before implementation

Current flow: TripSetup selects a city, calls Explore For you, filters starter results, reduces Places to names, combines them with cityStartSuggestions/defaultStartSuggestions/fallbackStartSuggestions, and renders string chips. Text input only edits local state. Generate sends startingArea as a string. TripService stores it in Trip.startingArea; TripMapper returns it unchanged. No dedicated start-area DTO/service/search exists.

Root cause: frontend template filling and static city lists are treated as geographic options; provider coordinates/identity are discarded. Explore supplies travel POIs, not a verified start-area contract, and retains its separate permitted starter fallback.

Files: mobile TripSetupScreen, api/journyApi.ts and types.ts; backend TripService, Trip/CreateTripRequest/TripResponse/TripMapper; existing DestinationResolutionService, PlaceProvider interface and OsmOverpassPlaceProvider.

Frontend fallbacks: generic Hotel area/Main station/Old town/City center; cityStartSuggestions; fallbackStartSuggestions known-city arrays; destination + city center/main station/old town/museum area.

Backend fallbacks: ExploreService and Place constructor contain generic display addresses. These are unrelated to the new endpoint and explicitly remain out of scope. No backend start-area fallback exists.

Reuse: real DestinationResolutionService, existing PlaceProvider abstraction, OSM transport/address failover, coordinate bbox helper, radius validation pattern, provider + OSM element identity. Extend the adapter with a separate start-area operation without changing itinerary queries, categories, ranking, cache, or stop validation. Start-area cache is bounded, short-lived and keyed by resolved geography plus search query.

Persistence: optional legacy string only today. Add nullable identity columns and a new additive migration; preserve historical strings. Newly submitted selections must be revalidated against server-discovered candidates, never trust client coordinates. Unselected search input is not a resolved selection and is not sent as a trip start location.
