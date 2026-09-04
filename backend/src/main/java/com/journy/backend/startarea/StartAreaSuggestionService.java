package com.journy.backend.startarea;

import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.explore.provider.PlaceProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.LoggerFactory;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class StartAreaSuggestionService {
    private final DestinationResolutionService destinations;
    private final List<PlaceProvider> providers;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private record Cached(Instant expires, List<StartAreaSuggestion> items) {}
    public StartAreaSuggestionService(DestinationResolutionService destinations, List<PlaceProvider> providers) {
        this.destinations = destinations; this.providers = providers;
    }
    public List<StartAreaSuggestion> suggestions(String destination, String query) {
        if (destination == null || destination.isBlank()) return List.of();
        return destinations.resolve(destination).map(d -> {
            // Distinct OSM entities can have identical chip labels (for example adjacent transit facilities).
            // Display the highest-priority/nearest one; keep their separate identities in the verification pool.
            Set<String> labels = new HashSet<>();
            return discover(d, query).stream().filter(p -> labels.add(normalize(p.name()) + ":" + p.type())).limit(6).toList();
        }).orElse(List.of());
    }
    public StartAreaSuggestion verify(ResolvedDestination destination, StartAreaSuggestion requested, String legacyText) {
        if (requested == null && (legacyText == null || legacyText.isBlank())) return null;
        String name = requested == null ? legacyText : requested.name();
        if (name == null || name.isBlank() || name.length() > 255) throw mismatch();
        var candidates = new ArrayList<>(discover(destination, ""));
        if (candidates.stream().noneMatch(p -> matches(p, requested, name))) candidates.addAll(discover(destination, name));
        return candidates.stream().filter(p -> matches(p, requested, name)).findFirst().orElseThrow(this::mismatch);
    }
    private boolean matches(StartAreaSuggestion canonical, StartAreaSuggestion requested, String name) {
        return requested == null ? normalize(canonical.name()).equals(normalize(name)) : canonical.id().equals(requested.id());
    }
    private ResponseStatusException mismatch() {
        return new ResponseStatusException(BAD_REQUEST, "Choose a verified starting area in this destination or leave it empty.");
    }
    private List<StartAreaSuggestion> discover(ResolvedDestination destination, String query) {
        String text = query == null ? "" : query.trim();
        if (text.length() > 255) return List.of();
        String key = destination.provider() + ":" + destination.providerPlaceId() + ":" + destination.latitude()
                + ":" + destination.longitude() + ":" + text.toLowerCase(Locale.ROOT);
        Cached hit = cache.get(key);
        if (hit != null && hit.expires().isAfter(Instant.now())) {
            LoggerFactory.getLogger(getClass()).info("start_area_cache hit locality={} query={} count={}", destination.locality(), text, hit.items().size());
            return hit.items();
        }
        Map<String, StartAreaSuggestion> unique = new LinkedHashMap<>();
        for (PlaceProvider provider : providers) {
            try {
                for (StartAreaSuggestion p : provider.searchStartAreas(destination, text, 60)) {
                    if (!valid(destination, p) || (!text.isBlank() && !normalize(p.name()).contains(normalize(text)))) continue;
                    String identity = p.providerPlaceId() != null && !p.providerPlaceId().isBlank()
                            ? p.source() + ":" + p.providerPlaceId()
                            : p.source() + ":" + normalize(p.name()) + ":" + Math.round(p.latitude() * 10000) + ":" + Math.round(p.longitude() * 10000);
                    // Stable fallback identity never depends on the result order or a city whitelist.
                    String id = p.source().substring("provider:".length()) + "_" + UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    unique.putIfAbsent(identity, new StartAreaSuggestion(id, p.name(), p.type(), p.latitude(), p.longitude(), p.source(), p.providerPlaceId()));
                }
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(getClass()).warn("start_area_provider_failed provider={} locality={}", provider.name(), destination.locality());
            }
        }
        List<StartAreaSuggestion> result = unique.values().stream().sorted(
                Comparator.comparingInt((StartAreaSuggestion p) -> !text.isBlank() && normalize(p.name()).equals(normalize(text)) ? 0 : 1)
                        .thenComparingInt(p -> priority(p.type()))
                        .thenComparingDouble(p -> distanceKm(destination.latitude(), destination.longitude(), p.latitude(), p.longitude()))
                        .thenComparing(p -> p.providerPlaceId() == null ? 1 : 0)
                        .thenComparing(StartAreaSuggestion::name).thenComparing(StartAreaSuggestion::id)).toList();
        if (cache.size() >= 256) cache.clear();
        // Empty/error results expire quickly; a provider recovery can be retried without synthetic filler.
        cache.put(key, new Cached(Instant.now().plusSeconds(result.isEmpty() ? 15 : 300), result));
        LoggerFactory.getLogger(getClass()).info("start_area_discovery locality={} latitude={} longitude={} query={} accepted={}",
                destination.locality(), destination.latitude(), destination.longitude(), text, result.size());
        return result;
    }
    static boolean valid(ResolvedDestination d, StartAreaSuggestion p) {
        return p != null && p.name() != null && !p.name().isBlank() && p.name().length() <= 255
                && p.source() != null && p.source().matches("provider:[a-zA-Z0-9_-]+")
                && !Set.of("provider:seed", "provider:starter", "provider:planned_fallback").contains(p.source().toLowerCase(Locale.ROOT))
                && p.latitude() != null && p.longitude() != null && Double.isFinite(p.latitude()) && Double.isFinite(p.longitude())
                && Math.abs(p.latitude()) <= 90 && Math.abs(p.longitude()) <= 180 && !(p.latitude() == 0 && p.longitude() == 0)
                && distanceKm(d.latitude(), d.longitude(), p.latitude(), p.longitude()) <= 9;
    }
    static double distanceKm(double lat, double lon, double otherLat, double otherLon) {
        double a = Math.pow(Math.sin(Math.toRadians(otherLat - lat) / 2), 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(otherLat)) * Math.pow(Math.sin(Math.toRadians(otherLon - lon) / 2), 2);
        return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
    private static int priority(String type) {
        if (type == null) return 5;
        return switch(type) { case "transit_station" -> 1; case "neighborhood", "district" -> 2; case "square" -> 3; case "landmark" -> 4; default -> 5; };
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
