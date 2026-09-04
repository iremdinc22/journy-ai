package com.journy.backend.ai.service;

import com.journy.backend.ai.dto.AiChatRequest;
import com.journy.backend.ai.dto.AiChatResponse;
import com.journy.backend.ai.dto.AiItineraryApplyRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionResponse;
import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.mapper.ItineraryMapper;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.ai.mapper.AiMapper;
import com.journy.backend.ai.model.AiConversation;
import com.journy.backend.ai.model.AiMessage;
import com.journy.backend.ai.repository.AiConversationRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

@Service
public class AiService {
    private final CurrentUserService currentUserService;
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryMapper itineraryMapper;
    private final AiConversationRepository aiConversationRepository;
    private final AiMapper aiMapper;

    public AiService(
            CurrentUserService currentUserService,
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryMapper itineraryMapper,
            AiConversationRepository aiConversationRepository,
            AiMapper aiMapper
    ) {
        this.currentUserService = currentUserService;
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryMapper = itineraryMapper;
        this.aiConversationRepository = aiConversationRepository;
        this.aiMapper = aiMapper;
    }

    @Transactional(readOnly = true)
    public AiItinerarySuggestionResponse itinerarySuggestion(AiItinerarySuggestionRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip was not found"));
        ItineraryDay day = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                .filter(foundDay -> foundDay.getDayNumber() == request.dayNumber())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));

        String action = request.action().toLowerCase();
        boolean Turkish = isTurkish(request.language());
        if (action.contains("rain") || action.contains("weather")) {
            return new AiItinerarySuggestionResponse("Weather forecast required",
                    "Check Plan for a destination/date-specific weather preview. No change has been applied.",
                    "Check forecast in Plan", null, List.of(), "Existing itinerary retained.");
        }
        if (action.contains("food")) {
            return buildFoodSuggestion(day, trip, Turkish);
        }
        if (action.contains("replace")) {
            return buildReplaceSuggestion(day, trip, Turkish);
        }
        return buildLighterSuggestion(day, trip, Turkish);
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse applyItinerarySuggestion(AiItineraryApplyRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip was not found"));
        ItineraryDay day = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                .filter(foundDay -> foundDay.getDayNumber() == request.dayNumber())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));

        String action = request.action().toLowerCase();
        boolean Turkish = isTurkish(request.language());
        if (action.contains("food") || action.contains("rain") || action.contains("weather")
                || action.contains("budget") || action.contains("cheap") || action.contains("replace")) {
            throw new com.journy.backend.common.exception.InsufficientDestinationDataException(1, 0);
        }
        applyLighterDay(day, Turkish);

        normalizeStopOrder(day);
        ItineraryDay savedDay = itineraryDayRepository.save(day);
        refreshTripStats(trip);
        return itineraryMapper.toDayResponse(savedDay);
    }

    @Transactional
    public AiChatResponse chat(AiChatRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId()).orElse(null);
        AiConversation conversation = resolveConversation(user, trip);
        conversation.addMessage(new AiMessage("USER", request.message()));

        AiDecision decision = decide(request.message(), trip);
        conversation.addMessage(new AiMessage("ASSISTANT", decision.message()));
        AiConversation savedConversation = aiConversationRepository.save(conversation);

        return aiMapper.toChatResponse(
                savedConversation,
                decision.message(),
                decision.suggestedAction(),
                decision.minutesSaved()
        );
    }

    private AiDecision decide(String message, Trip trip) {
        String text = message.toLowerCase();
        TripContext context = buildTripContext(trip);
        if (text.contains("coffee") || text.contains("cafe")) {
            return new AiDecision(
                    "I found a quiet coffee break for " + context.destination() + ". It fits best around " + context.breakWindow() + " and keeps the route close to your " + context.paceLabel() + " pace.",
                    "Add coffee stop",
                    0
            );
        }
        if (text.contains("rain") || text.contains("weather")) {
            return new AiDecision(
                    "Check Plan for a destination/date-specific weather preview. No change has been applied.",
                    "Check forecast in Plan",
                    0
            );
        }
        if (text.contains("dinner") || text.contains("food")) {
            return new AiDecision(
                    "Stay near the final neighborhood for dinner in " + context.destination() + ". With a " + context.budgetLabel() + " budget, I would choose a local-first place close to the last stop instead of adding a long transfer.",
                    "Suggest dinner area",
                    12
            );
        }
        if (text.contains("light") || text.contains("easy") || text.contains("short")) {
            return new AiDecision(
                    "I would keep the strongest anchor stop, remove one optional stop and add a longer break after lunch. Your " + context.stopSummary() + " plan stays realistic, but the day feels lighter.",
                    "Lighten day",
                    22
            );
        }

        return new AiDecision(
                "I can help with that for " + context.destination() + ". I would keep the main anchor stops, reduce backtracking and leave one flexible window so the route still matches your " + context.paceLabel() + " style.",
                "Adjust route",
                null
        );
    }

    private TripContext buildTripContext(Trip trip) {
        if (trip == null) {
            return new TripContext("your trip", "balanced", "mid-range", "current", "current");
        }

        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        String breakWindow = days.stream()
                .flatMap(day -> day.getStops().stream())
                .filter(stop -> stop.getCategory().equalsIgnoreCase("COFFEE") || stop.getCategory().equalsIgnoreCase("FOOD"))
                .map(ItineraryStop::getTimeWindow)
                .findFirst()
                .orElse("your afternoon break");
        String stopSummary = trip.getTotalStops() > 0
                ? trip.getTotalStops() + "-stop"
                : Math.max(1, days.stream().mapToInt(day -> day.getStops().size()).sum()) + "-stop";
        return new TripContext(
                trip.getDestination(),
                trip.getPace().name().toLowerCase().replace('_', ' '),
                trip.getBudget().name().toLowerCase().replace('_', ' '),
                breakWindow,
                stopSummary
        );
    }

    private Optional<Trip> resolveTrip(UserAccount user, String tripId) {
        if (tripId != null && !tripId.isBlank()) {
            return tripRepository.findById(tripId)
                    .filter(trip -> trip.getUser().getId().equals(user.getId()));
        }
        return tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail());
    }

    private AiConversation resolveConversation(UserAccount user, Trip trip) {
        if (trip != null) {
            return aiConversationRepository
                    .findFirstByUserEmailIgnoreCaseAndTripIdOrderByUpdatedAtDesc(user.getEmail(), trip.getId())
                    .orElseGet(() -> aiConversationRepository.save(new AiConversation(user, trip, trip.getDestination() + " assistant")));
        }
        return aiConversationRepository
                .findFirstByUserEmailIgnoreCaseAndTripIsNullOrderByUpdatedAtDesc(user.getEmail())
                .orElseGet(() -> aiConversationRepository.save(new AiConversation(user, null, "Travel assistant")));
    }

    private AiItinerarySuggestionResponse buildLighterSuggestion(ItineraryDay day, Trip trip, boolean Turkish) {
        ItineraryStop affectedStop = day.getStops().isEmpty() ? null : day.getStops().getLast();
        List<String> affected = affectedStop == null ? List.of() : List.of(affectedStop.getTitle());
        int minutesSaved = Math.max(14, (int) Math.round(day.getWalkKm() * 3.4));
        if (Turkish) {
            return new AiItinerarySuggestionResponse(
                    day.getDayNumber() + ". günü hafiflet",
                    "En güçlü ana durakları koruyup son durağı opsiyonel bir aralığa çeviririm. Böylece ana deneyim korunurken günün baskısı azalır.",
                    "Opsiyonel son durağı kaldır",
                    minutesSaved,
                    affected,
                    trip.getDestination() + " " + day.getDayNumber() + ". gün daha sakin " + Math.max(2, day.getStops().size() - 1) + " duraklı rotaya dönüşür."
            );
        }
        return new AiItinerarySuggestionResponse(
                "Make Day " + day.getDayNumber() + " lighter",
                "Keep the strongest anchor stops and turn the final stop into an optional window. This protects the main experience while reducing pressure.",
                "Remove optional final stop",
                minutesSaved,
                affected,
                trip.getDestination() + " Day " + day.getDayNumber() + " becomes a calmer " + Math.max(2, day.getStops().size() - 1) + "-stop route."
        );
    }

    private AiItinerarySuggestionResponse buildFoodSuggestion(ItineraryDay day, Trip trip, boolean Turkish) {
        ItineraryStop anchor = day.getStops().stream()
                .filter(stop -> stop.getCategory().equalsIgnoreCase("FOOD") || stop.getCategory().equalsIgnoreCase("COFFEE"))
                .findFirst()
                .orElse(day.getStops().isEmpty() ? null : day.getStops().get(Math.min(1, day.getStops().size() - 1)));
        List<String> affected = anchor == null ? List.of() : List.of(anchor.getTitle());
        if (Turkish) {
            return new AiItinerarySuggestionResponse(
                    "Yakına yemek molası ekle",
                    "Seni şehrin öbür ucuna göndermeden mevcut rotanın yakınına yerel yemek veya kahve durağı eklerim.",
                    "Rotaya yakın yemek durağı ekle",
                    0,
                    affected,
                    trip.getDestination() + " " + day.getDayNumber() + ". gün aynı rota şeklini korur ama daha iyi bir mola aralığı kazanır."
            );
        }
        return new AiItinerarySuggestionResponse(
                "Add a nearby food break",
                "Add one local food or coffee stop near the current route instead of sending you across the city.",
                "Add food stop near route",
                0,
                affected,
                trip.getDestination() + " Day " + day.getDayNumber() + " keeps the same route shape with a better break window."
        );
    }

    private AiItinerarySuggestionResponse buildReplaceSuggestion(ItineraryDay day, Trip trip, boolean Turkish) {
        ItineraryStop affectedStop = day.getStops().size() > 2 ? day.getStops().get(1) : day.getStops().stream().findFirst().orElse(null);
        List<String> affected = affectedStop == null ? List.of() : List.of(affectedStop.getTitle());
        if (Turkish) {
            return new AiItinerarySuggestionResponse(
                    "Bir durağı değiştir",
                    "En zayıf uyumlu durağı aynı bölgede başka bir seçenekle değiştiririm; gün değişir ama rota bozulmaz.",
                    "Aynı bölgede durağı değiştir",
                    10,
                    affected,
                    trip.getDestination() + " " + day.getDayNumber() + ". gün yürünebilir kalır ve tercihlerine daha iyi uyar."
            );
        }
        return new AiItinerarySuggestionResponse(
                "Replace one stop",
                "Swap the weakest-fit stop for another option in the same area so the day changes without breaking the route.",
                "Replace stop in same area",
                10,
                affected,
                trip.getDestination() + " Day " + day.getDayNumber() + " stays walkable while matching your preferences more closely."
        );
    }

    private void applyLighterDay(ItineraryDay day, boolean Turkish) {
        day.setTitle(lightTitle(day.getTitle()));
        if (day.getStops().size() <= 2) {
            day.setSummary(Turkish
                    ? "Journy ana durakları korudu; bu gün zaten hafif tempoya uygun."
                    : "Journy kept the core stops and marked the pace as already light.");
            day.setWalkKm(Math.max(2.4, Math.round((day.getWalkKm() - 0.4) * 10.0) / 10.0));
            return;
        }
        day.getStops().removeLast();
        day.setSummary(Turkish
                ? "Son esnek durak çıkarıldı; ana duraklar arasında daha fazla boşluk bırakıldı."
                : "A lighter version of the day with the final optional stop removed and more room between anchors.");
        day.setWalkKm(Math.max(2.4, Math.round((day.getWalkKm() - 1.1) * 10.0) / 10.0));
    }

    private boolean isTurkish(String language) {
        return language != null && language.equalsIgnoreCase("tr");
    }

    private String lightTitle(String title) {
        return title.toLowerCase().startsWith("lighter ") ? title : "Lighter " + title;
    }

    private void normalizeStopOrder(ItineraryDay day) {
        for (int index = 0; index < day.getStops().size(); index++) {
            day.getStops().get(index).setStopOrder(index + 1);
        }
    }

    private void refreshTripStats(Trip trip) {
        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        int totalStops = days.stream().mapToInt(day -> day.getStops().size()).sum();
        int foodPicks = (int) days.stream()
                .flatMap(day -> day.getStops().stream())
                .filter(stop -> stop.getCategory().equalsIgnoreCase("FOOD") || stop.getCategory().equalsIgnoreCase("COFFEE"))
                .count();
        double averageWalk = days.stream().mapToDouble(ItineraryDay::getWalkKm).average().orElse(0);
        trip.setTotalStops(totalStops);
        trip.setFoodPicks(foodPicks);
        trip.setAverageWalkKm(Math.round(averageWalk * 10.0) / 10.0);
        tripRepository.save(trip);
    }

    private record AiDecision(String message, String suggestedAction, Integer minutesSaved) {
    }

    private record TripContext(String destination, String paceLabel, String budgetLabel, String breakWindow, String stopSummary) {
    }
}
