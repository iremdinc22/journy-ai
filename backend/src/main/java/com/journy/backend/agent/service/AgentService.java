package com.journy.backend.agent.service;

import com.journy.backend.agent.client.PythonAgentClient;
import com.journy.backend.agent.dto.AgentActionPreview;
import com.journy.backend.agent.dto.AgentContext;
import com.journy.backend.agent.dto.AgentApplyRequest;
import com.journy.backend.agent.dto.AgentMessageRequest;
import com.journy.backend.agent.dto.AgentMessageResponse;
import com.journy.backend.agent.enums.AgentIntent;
import com.journy.backend.ai.dto.AiItinerarySuggestionRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionResponse;
import com.journy.backend.ai.service.AiService;
import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.mapper.ItineraryMapper;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AgentService {
    private final CurrentUserService currentUserService;
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final AiService aiService;
    private final PythonAgentClient pythonAgentClient;
    private final AgentContextBuilder agentContextBuilder;
    private final ItineraryMapper itineraryMapper;

    public AgentService(
            CurrentUserService currentUserService,
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            AiService aiService,
            PythonAgentClient pythonAgentClient,
            AgentContextBuilder agentContextBuilder,
            ItineraryMapper itineraryMapper
    ) {
        this.currentUserService = currentUserService;
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.aiService = aiService;
        this.pythonAgentClient = pythonAgentClient;
        this.agentContextBuilder = agentContextBuilder;
        this.itineraryMapper = itineraryMapper;
    }

    @Transactional(readOnly = true)
    public AgentMessageResponse message(AgentMessageRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId());
        List<ItineraryDay> itineraryDays = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        ItineraryDay day = resolveDay(itineraryDays, request.dayNumber(), request.message());
        AgentContext context = agentContextBuilder.build(user, trip, day, itineraryDays);
        AgentIntent intent = detectIntent(request.message());
        boolean Turkish = isTurkish(request.language());
        if (intent != AgentIntent.GENERAL_GUIDANCE) {
            AgentActionPreview preview = buildPreview(intent, trip, day, itineraryDays, context, request.language());
            return new AgentMessageResponse(
                    "agent_" + trip.getId(),
                    buildAgentMessage(intent, trip, day, preview, itineraryDays, context, Turkish),
                    intent,
                    preview
            );
        }

        AgentMessageResponse pythonResponse = pythonAgentClient.message(request.message(), context, request.language()).orElse(null);
        if (pythonResponse != null) {
            return new AgentMessageResponse(
                    "agent_" + trip.getId(),
                    pythonResponse.message(),
                    pythonResponse.intent(),
                    pythonResponse.preview()
            );
        }

        AgentActionPreview preview = buildPreview(intent, trip, day, itineraryDays, context, request.language());

        return new AgentMessageResponse(
                "agent_" + trip.getId(),
                buildAgentMessage(intent, trip, day, preview, itineraryDays, context, Turkish),
                intent,
                preview
        );
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse apply(AgentApplyRequest request) {
        AgentIntent intent = request.intent();
        if (intent == AgentIntent.GENERAL_GUIDANCE) {
            intent = AgentIntent.MAKE_DAY_LIGHTER;
        }
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId());
        ItineraryDay day = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                .filter(foundDay -> foundDay.getDayNumber() == request.dayNumber())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));
        boolean Turkish = isTurkish(request.language());

        switch (intent) {
            case MAKE_DAY_LIGHTER -> applyMakeDayLighter(day, Turkish);
            case ADD_FOOD_STOP -> applyAddFoodStop(trip, day, Turkish);
            case REPLACE_STOP -> applyReplaceStop(trip, day, Turkish);
            case BUDGET_OPTIMIZE -> applyBudgetOptimize(trip, day, Turkish);
            case RAIN_REPLAN -> applyRainReplan(trip, day, Turkish);
            case GENERAL_GUIDANCE -> applyMakeDayLighter(day, Turkish);
        }

        normalizeStopOrder(day);
        ItineraryDay savedDay = itineraryDayRepository.save(day);
        refreshTripStats(trip);
        return itineraryMapper.toDayResponse(savedDay);
    }

    private AgentActionPreview buildPreview(AgentIntent intent, Trip trip, ItineraryDay day, List<ItineraryDay> itineraryDays, AgentContext context, String language) {
        boolean Turkish = isTurkish(language);
        if (intent == AgentIntent.BUDGET_OPTIMIZE) {
            return budgetPreview(trip, day, context, Turkish);
        }
        if (intent == AgentIntent.RAIN_REPLAN) {
            return rainPreview(trip, day, context, Turkish);
        }
        if (intent == AgentIntent.GENERAL_GUIDANCE) {
            return guidancePreview(trip, day, context, Turkish);
        }

        AiItinerarySuggestionResponse suggestion = aiService.itinerarySuggestion(new AiItinerarySuggestionRequest(
                trip.getId(),
                day.getDayNumber(),
                actionFor(intent),
                language
        ));

        return new AgentActionPreview(
                intent,
                suggestion.title(),
                suggestion.message(),
                suggestion.suggestedAction(),
                suggestion.minutesSaved(),
                suggestion.stopsAffected(),
                suggestion.routeSummary(),
                explain(intent, trip, day, suggestion.stopsAffected(), itineraryDays, context, Turkish),
                true
        );
    }

    private AgentActionPreview budgetPreview(Trip trip, ItineraryDay day, AgentContext context, boolean Turkish) {
        List<String> affectedStops = day.getStops().stream()
                .filter(stop -> stop.getCategory().equalsIgnoreCase("FOOD") || stop.getCategory().equalsIgnoreCase("COFFEE"))
                .map(ItineraryStop::getTitle)
                .limit(2)
                .toList();
        return new AgentActionPreview(
                AgentIntent.BUDGET_OPTIMIZE,
                Turkish ? day.getDayNumber() + ". günü bütçeye göre düzenle" : "Optimize Day " + day.getDayNumber() + " for budget",
                Turkish ? "Ana durakları koruyup esnek bir seçimi daha ekonomik bir alternatifle değiştiririm." : "Keep the main stops. Swap one flexible pick for a lower-cost option.",
                Turkish ? "Bütçe dostu alternatif kullan" : "Use a budget-friendly alternative",
                8,
                affectedStops,
                Turkish ? day.getDayNumber() + ". gün aynı rota alanında kalır." : "Day " + day.getDayNumber() + " stays close to the same route.",
                List.of(
                        Turkish ? "Bütçe modun dikkate alındı" : trip.getBudget().name().toLowerCase().replace('_', ' ') + " budget",
                        personalizationReason(context, Turkish),
                        Turkish ? "Aynı bölge" : "Same area"
                ).stream().distinct().limit(2).toList(),
                true
        );
    }

    private AgentActionPreview rainPreview(Trip trip, ItineraryDay day, AgentContext context, boolean Turkish) {
        List<String> outdoorStops = day.getStops().stream()
                .filter(stop -> stop.getCategory().equalsIgnoreCase("WALKING") || stop.getCategory().equalsIgnoreCase("FREE"))
                .map(ItineraryStop::getTitle)
                .limit(2)
                .toList();
        return new AgentActionPreview(
                AgentIntent.RAIN_REPLAN,
                Turkish ? day.getDayNumber() + ". günü yağmura göre düzenle" : "Rebuild Day " + day.getDayNumber() + " around rain",
                Turkish ? "Riskli açık hava durağını kapalı mekana uygun bir zaman aralığına taşırım." : "Move the risky outdoor stop to an indoor-friendly window.",
                Turkish ? "Kapalı mekana uygun durak kullan" : "Use an indoor-friendly stop",
                12,
                outdoorStops,
                Turkish ? day.getDayNumber() + ". gün yağmura daha hazır hale gelir." : "Day " + day.getDayNumber() + " becomes rain-ready.",
                List.of(
                        Turkish ? "Açık hava durağı bulundu" : "Outdoor stop found",
                        personalizationReason(context, Turkish),
                        Turkish ? "Aynı bölge" : "Same area"
                ).stream().distinct().limit(2).toList(),
                true
        );
    }

    private AgentActionPreview guidancePreview(Trip trip, ItineraryDay day, AgentContext context, boolean Turkish) {
        return new AgentActionPreview(
                AgentIntent.GENERAL_GUIDANCE,
                Turkish ? "Bu günü düzenleyebilirim" : "I can adjust this day",
                Turkish ? "Daha hafif, ekonomik, yemek odaklı veya yağmura hazır bir gün isteyebilirsin." : "Ask for a lighter, cheaper, food-focused or rain-ready day.",
                Turkish ? "Bu günü düzenle" : "Adjust this day",
                null,
                List.of(),
                Turkish
                        ? day.getDayNumber() + ". gün: " + day.getStops().size() + " durak, " + day.getWalkKm() + " km."
                        : "Day " + day.getDayNumber() + ": " + day.getStops().size() + " stops, " + day.getWalkKm() + " km.",
                List.of(
                        Turkish ? "Mevcut gezi planı" : "Current itinerary",
                        personalizationReason(context, Turkish),
                        Turkish ? "Önce önizleme" : "Preview first"
                ).stream().distinct().limit(2).toList(),
                false
        );
    }

    private String buildAgentMessage(
            AgentIntent intent,
            Trip trip,
            ItineraryDay day,
            AgentActionPreview preview,
            List<ItineraryDay> itineraryDays,
            AgentContext context,
            boolean Turkish
    ) {
        if (!preview.requiresConfirmation()) {
            return preview.message();
        }
        ItineraryDay busiestDay = findBusiestDay(itineraryDays);
        if (Turkish) {
            return switch (intent) {
                case MAKE_DAY_LIGHTER -> busiestDay.getDayNumber() == day.getDayNumber() && itineraryDays.size() > 1
                        ? day.getDayNumber() + ". gün en yoğun gün. Esnek bir durağı çıkarabilirim."
                        : day.getDayNumber() + ". günü hafifletebilirim.";
                case ADD_FOOD_STOP -> "Rotanın yakınına yerel bir mola ekleyebilirim.";
                case REPLACE_STOP -> "Aynı bölgede zayıf uyumlu bir durağı değiştirebilirim.";
                case BUDGET_OPTIMIZE -> "Bu günü bütçe açısından daha rahat hale getirebilirim.";
                case RAIN_REPLAN -> "Günü kapalı mekana daha uygun hale getirebilirim.";
                case GENERAL_GUIDANCE -> preview.message();
            };
        }
        return switch (intent) {
            case MAKE_DAY_LIGHTER -> busiestDay.getDayNumber() == day.getDayNumber() && itineraryDays.size() > 1
                    ? "Day " + day.getDayNumber() + " is the busiest. I can remove one flexible stop."
                    : "I can make Day " + day.getDayNumber() + " lighter.";
            case ADD_FOOD_STOP -> "I can add a local break near the route.";
            case REPLACE_STOP -> "I can swap one weak-fit stop in the same area.";
            case BUDGET_OPTIMIZE -> "I can make this day easier on budget.";
            case RAIN_REPLAN -> "I can move the day toward indoor-friendly stops.";
            case GENERAL_GUIDANCE -> preview.message();
        };
    }

    private List<String> explain(
            AgentIntent intent,
            Trip trip,
            ItineraryDay day,
            List<String> affectedStops,
            List<ItineraryDay> itineraryDays,
            AgentContext context,
            boolean Turkish
    ) {
        String affected = affectedStops.isEmpty() ? "the flexible route window" : affectedStops.getFirst();
        ItineraryDay busiestDay = findBusiestDay(itineraryDays);
        if (Turkish) {
            String affectedTr = affectedStops.isEmpty() ? "esnek rota aralığı" : affectedStops.getFirst();
            return switch (intent) {
                case MAKE_DAY_LIGHTER -> List.of(
                        affectedTr + " esnek",
                        "Bugün " + day.getWalkKm() + " km yürüyüş var",
                        personalizationReason(context, true),
                        busiestDay.getDayNumber() == day.getDayNumber() && itineraryDays.size() > 1
                                ? "En yoğun gün"
                                : "Ana duraklar korunur"
                ).stream().distinct().limit(2).toList();
                case ADD_FOOD_STOP -> List.of(
                        "Yerel mola",
                        personalizationReason(context, true),
                        "Rotaya yakın"
                ).stream().distinct().limit(2).toList();
                case REPLACE_STOP -> List.of(
                        affectedTr + " değişebilir",
                        savedPlaceReason(context, true),
                        "Aynı zaman aralığı"
                ).stream().distinct().limit(2).toList();
                default -> List.of(
                        "Mevcut gezi planı",
                        "Önce önizleme"
                );
            };
        }
        return switch (intent) {
            case MAKE_DAY_LIGHTER -> List.of(
                    affected + " is flexible",
                    day.getWalkKm() + " km today",
                    personalizationReason(context, false),
                    busiestDay.getDayNumber() == day.getDayNumber() && itineraryDays.size() > 1
                            ? "Busiest day"
                            : "Main stops stay"
            ).stream().distinct().limit(2).toList();
            case ADD_FOOD_STOP -> List.of(
                    "Local break",
                    personalizationReason(context, false),
                    "Near route"
            ).stream().distinct().limit(2).toList();
            case REPLACE_STOP -> List.of(
                    affected + " can move",
                    savedPlaceReason(context, false),
                    "Same time window"
            ).stream().distinct().limit(2).toList();
            default -> List.of(
                    "Current itinerary",
                    "Preview first"
            );
        };
    }

    private String personalizationReason(AgentContext context, boolean Turkish) {
        return Turkish ? primaryTaste(context) + " tercihine uyuyor" : "Matches " + primaryTaste(context);
    }

    private String savedPlaceReason(AgentContext context, boolean Turkish) {
        List<String> savedSignals = context.userProfile().savedCategorySignals();
        if (savedSignals == null || savedSignals.isEmpty()) {
            return Turkish ? "Kurulum tercihlerine uyuyor" : "Matches setup";
        }
        return Turkish ? "Kayıtlı yerler: " + savedSignals.getFirst().replace(" x", "x") : "Saved places: " + savedSignals.getFirst().replace(" x", "x");
    }

    private String primaryTaste(AgentContext context) {
        List<String> signals = context.userProfile().tasteSignals();
        if (signals == null || signals.isEmpty()) {
            return "balanced travel";
        }
        return signals.getFirst().toLowerCase();
    }

    private AgentIntent detectIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "budget", "cheap", "cheaper", "save money", "ucuz", "bütçe", "tasarruf", "euro")) {
            return AgentIntent.BUDGET_OPTIMIZE;
        }
        if (containsAny(text, "rain", "weather", "rainy", "indoor", "inside", "covered", "rain-ready", "yağmur", "hava", "kapalı")) {
            return AgentIntent.RAIN_REPLAN;
        }
        if (containsAny(text, "coffee", "cafe", "food", "dinner", "restaurant", "kahve", "yemek", "akşam")) {
            return AgentIntent.ADD_FOOD_STOP;
        }
        if (containsAny(text, "replace", "swap", "change stop", "değiştir", "yerine")) {
            return AgentIntent.REPLACE_STOP;
        }
        if (containsAny(text, "light", "lighter", "easy", "short", "slow", "less walking", "tired", "finish earlier", "hafif", "yorul", "yorgun", "kolay", "az yür", "erken bit")) {
            return AgentIntent.MAKE_DAY_LIGHTER;
        }
        return AgentIntent.GENERAL_GUIDANCE;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String actionFor(AgentIntent intent) {
        return switch (intent) {
            case ADD_FOOD_STOP -> "food";
            case RAIN_REPLAN -> "rain";
            case BUDGET_OPTIMIZE -> "budget";
            case REPLACE_STOP -> "replace";
            default -> "lighter";
        };
    }

    private boolean isTurkish(String language) {
        return language != null && language.equalsIgnoreCase("tr");
    }

    private void applyMakeDayLighter(ItineraryDay day, boolean Turkish) {
        if (day.getStops().size() <= 2) {
            day.getStops().forEach(stop -> stop.setOptionalStop(true));
            day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - 0.7)));
            day.setSummary(Turkish
                    ? "Journy bu kompakt günü opsiyonel kullanıma uygun hale getirdi; rota bozulmadan yavaşlayabilirsin."
                    : "Journy marked this compact day as optional-friendly so you can slow down without losing the route shape.");
            return;
        }

        ItineraryStop removable = day.getStops().stream()
                .filter(ItineraryStop::isOptionalStop)
                .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                .orElseGet(() -> day.getStops().stream()
                        .filter(stop -> !isFoodOrCoffee(stop))
                        .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                        .orElse(day.getStops().getLast()));
        day.getStops().remove(removable);
        day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - walkReductionFor(removable))));
        day.setSummary(Turkish
                ? "Journy " + removable.getTitle() + " durağını çıkardı; ana duraklar korunurken gün hafifledi."
                : "Journy removed " + removable.getTitle() + " to make the day lighter while keeping the strongest route anchors.");
    }

    private void applyAddFoodStop(Trip trip, ItineraryDay day, boolean Turkish) {
        int insertOrder = Math.min(day.getStops().size() + 1, 3);
        shiftStopsFrom(day, insertOrder);
        ItineraryStop anchor = day.getStops().stream()
                .filter(stop -> stop.getStopOrder() == Math.max(1, insertOrder - 1))
                .findFirst()
                .orElseGet(() -> day.getStops().isEmpty() ? null : day.getStops().getFirst());
        ItineraryStop stop = new ItineraryStop(
                insertOrder,
                foodBreakTitle(trip),
                "FOOD",
                "13:00",
                Turkish
                        ? "Journy AI mevcut rota aralığına yerel bir yemek molası ekledi."
                        : "Added by Journy AI as a local food break inside the existing route window.",
                anchor == null ? 0 : anchor.getLatitude() + 0.002,
                anchor == null ? 0 : anchor.getLongitude() + 0.002
        );
        day.addStop(stop);
        day.setWalkKm(round(day.getWalkKm() + 0.4));
        day.setSummary(Turkish
                ? "Mevcut rotaya yakın yemek molası eklendi; gün belirgin ağırlaşmadan daha yerel hissettirir."
                : "Journy added a food break near the current route so the day feels more local without becoming much heavier.");
    }

    private void applyReplaceStop(Trip trip, ItineraryDay day, boolean Turkish) {
        ItineraryStop target = weakestFlexibleStop(day);
        target.setTitle(replacementTitle(trip, target));
        target.setCategory(replacementCategory(target));
        target.setNote(Turkish
                ? "Journy AI rotana, tempoya ve zevk profiline daha iyi uyması için değiştirdi."
                : "Replaced by Journy AI to better match your route, pace and taste profile.");
        target.setTimeWindow(replacementTimeWindow(target));
        target.setOptionalStop(false);
        day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - 0.2)));
        day.setSummary(Turkish
                ? "En zayıf uyumlu durak aynı rota aralığı korunarak değiştirildi."
                : "Journy replaced the weakest-fit stop while preserving the same route window.");
    }

    private void applyBudgetOptimize(Trip trip, ItineraryDay day, boolean Turkish) {
        ItineraryStop target = day.getStops().stream()
                .filter(this::isFoodOrCoffee)
                .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                .orElseGet(() -> weakestFlexibleStop(day));
        target.setTitle(budgetTitle(trip, target));
        target.setCategory(target.getCategory().equalsIgnoreCase("COFFEE") ? "COFFEE" : "FOOD");
        target.setNote(Turkish
                ? "Journy AI mevcut rotaya yakın daha ekonomik yerel bir seçenekle düzenledi."
                : "Adjusted by Journy AI toward a lower-cost local option near the existing route.");
        day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - 0.1)));
        day.setSummary(Turkish
                ? "Ana duraklar korundu; esnek yemek aralığı bütçeye daha uygun hale getirildi."
                : "Journy kept the main anchors and made the flexible food window more budget-friendly.");
    }

    private void applyRainReplan(Trip trip, ItineraryDay day, boolean Turkish) {
        ItineraryStop target = day.getStops().stream()
                .filter(this::isOutdoor)
                .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                .orElseGet(() -> weakestFlexibleStop(day));
        target.setTitle(indoorTitle(trip));
        target.setCategory("CULTURE");
        target.setTimeWindow("14:00");
        target.setNote(Turkish
                ? "Journy AI yağmura göre düzenledi: öğleden sonrayı kapalı mekana uygun durakla korudu."
                : "Rain-aware adjustment from Journy AI: protected the afternoon with an indoor-friendly stop.");
        target.setOptionalStop(false);
        day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - 0.5)));
        day.setSummary(Turkish
                ? "Yağmurlu saatler rotayı bozmasın diye gün kapalı kültür aralığına kaydırıldı."
                : "Journy moved the day toward an indoor culture window so rainy hours do not break the route.");
    }

    private ItineraryStop weakestFlexibleStop(ItineraryDay day) {
        return day.getStops().stream()
                .filter(ItineraryStop::isOptionalStop)
                .findFirst()
                .orElseGet(() -> day.getStops().stream()
                        .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                        .orElseThrow(() -> new ResourceNotFoundException("Itinerary stop was not found")));
    }

    private void shiftStopsFrom(ItineraryDay day, int order) {
        day.getStops().stream()
                .filter(stop -> stop.getStopOrder() >= order)
                .forEach(stop -> stop.setStopOrder(stop.getStopOrder() + 1));
    }

    private void normalizeStopOrder(ItineraryDay day) {
        day.getStops().sort(Comparator.comparingInt(ItineraryStop::getStopOrder));
        for (int index = 0; index < day.getStops().size(); index++) {
            day.getStops().get(index).setStopOrder(index + 1);
        }
    }

    private void refreshTripStats(Trip trip) {
        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        int totalStops = days.stream().mapToInt(day -> day.getStops().size()).sum();
        int foodPicks = (int) days.stream()
                .flatMap(day -> day.getStops().stream())
                .filter(this::isFoodOrCoffee)
                .count();
        double averageWalk = days.stream().mapToDouble(ItineraryDay::getWalkKm).average().orElse(0);
        trip.setTotalStops(totalStops);
        trip.setFoodPicks(foodPicks);
        trip.setAverageWalkKm(round(averageWalk));
        tripRepository.save(trip);
    }

    private boolean isFoodOrCoffee(ItineraryStop stop) {
        String category = normalize(stop.getCategory());
        return category.contains("FOOD") || category.contains("COFFEE");
    }

    private boolean isOutdoor(ItineraryStop stop) {
        String category = normalize(stop.getCategory());
        String title = normalize(stop.getTitle());
        return category.contains("WALKING")
                || category.contains("FREE")
                || title.contains("WALK")
                || title.contains("GARDEN")
                || title.contains("PARK")
                || title.contains("WATERFRONT");
    }

    private double walkReductionFor(ItineraryStop stop) {
        if (isOutdoor(stop)) {
            return 1.4;
        }
        if (isFoodOrCoffee(stop)) {
            return 0.6;
        }
        return 1.0;
    }

    private String foodBreakTitle(Trip trip) {
        return trip.getDestination() + " Local Lunch Break";
    }

    private String replacementTitle(Trip trip, ItineraryStop target) {
        String category = normalize(target.getCategory());
        if (category.contains("CULTURE")) {
            return trip.getDestination() + " Compact Culture Stop";
        }
        if (category.contains("COFFEE")) {
            return trip.getDestination() + " Quiet Coffee Window";
        }
        if (category.contains("FOOD")) {
            return trip.getDestination() + " Local Food Window";
        }
        return trip.getDestination() + " Easier Route Window";
    }

    private String replacementCategory(ItineraryStop target) {
        String category = normalize(target.getCategory());
        if (category.contains("COFFEE")) return "COFFEE";
        if (category.contains("FOOD")) return "FOOD";
        if (category.contains("CULTURE")) return "CULTURE";
        return "WALKING";
    }

    private String replacementTimeWindow(ItineraryStop target) {
        String category = normalize(target.getCategory());
        if (category.contains("COFFEE")) return "11:30";
        if (category.contains("FOOD")) return "13:00";
        if (category.contains("CULTURE")) return "14:00";
        return "16:00";
    }

    private String budgetTitle(Trip trip, ItineraryStop target) {
        if (target.getCategory().equalsIgnoreCase("COFFEE")) {
            return trip.getDestination() + " Low-Cost Coffee Break";
        }
        return trip.getDestination() + " Local Market Bite";
    }

    private String indoorTitle(Trip trip) {
        return trip.getDestination() + " Indoor Culture Window";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Trip resolveTrip(UserAccount user, String tripId) {
        if (tripId != null && !tripId.isBlank()) {
            return tripRepository.findById(tripId)
                    .filter(trip -> trip.getUser().getId().equals(user.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Trip was not found"));
        }
        return tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Current trip was not found"));
    }

    private ItineraryDay resolveDay(List<ItineraryDay> itineraryDays, Integer requestedDayNumber, String message) {
        if (isTripWideQuestion(message)) {
            return findBusiestDay(itineraryDays);
        }
        int dayNumber = requestedDayNumber == null ? 1 : requestedDayNumber;
        return itineraryDays.stream()
                .filter(day -> day.getDayNumber() == dayNumber)
                .findFirst()
                .orElseGet(() -> itineraryDays.stream()
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found")));
    }

    private boolean isTripWideQuestion(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return containsAny(
                text,
                "whole trip",
                "full trip",
                "which day",
                "best day",
                "all days",
                "trip overall",
                "tüm seyahat",
                "seyahat geneli",
                "hangi gün",
                "en yoğun gün"
        );
    }

    private ItineraryDay findBusiestDay(List<ItineraryDay> itineraryDays) {
        return itineraryDays.stream()
                .max((first, second) -> {
                    int walkingCompare = Double.compare(first.getWalkKm(), second.getWalkKm());
                    if (walkingCompare != 0) {
                        return walkingCompare;
                    }
                    return Integer.compare(first.getStops().size(), second.getStops().size());
                })
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));
    }
}
