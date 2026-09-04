package com.journy.backend.explore.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.destination.provider.DestinationCoordinates;
import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.place.enums.PlaceCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class OsmOverpassPlaceProvider implements PlaceProvider {
    private static final Logger log = LoggerFactory.getLogger(OsmOverpassPlaceProvider.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String endpoint;
    private final int radiusMeters;
    private final List<Integer> searchRadii;

    public OsmOverpassPlaceProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${journy.places.osm.enabled:true}") boolean enabled,
            @Value("${journy.places.osm.endpoint:https://overpass-api.de/api/interpreter}") String endpoint,
            @Value("${journy.places.osm.radius-meters:4500}") int radiusMeters
    ) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "Journy/1.0 place-provider")
                .requestFactory(requestFactory())
                .build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.radiusMeters = radiusMeters;
        this.searchRadii = List.of(radiusMeters, Math.max(radiusMeters * 2, 9000));
    }

    private HttpComponentsClientHttpRequestFactory requestFactory() {
        // Try alternate DNS addresses when an IPv4/IPv6 endpoint is unreachable.
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(23));
        return factory;
    }

    @Override
    public String name() {
        return "osm";
    }

    @Override
    public List<ExternalPlaceCandidate> search(ResolvedDestination destination, PlaceCategory category, int limit) {
        if (!enabled || destination == null || destination.locality() == null || destination.locality().isBlank()) {
            return List.of();
        }
        for (int radius : searchRadii) {
            List<ExternalPlaceCandidate> candidates = fetch(destination, category, limit, query(destination, category, limit, radius), radius);
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        return List.of();
    }

    /** Dedicated area discovery; itinerary search and its categories are unchanged. */
    @Override
    public List<com.journy.backend.startarea.StartAreaSuggestion> searchStartAreas(ResolvedDestination destination, String search, int limit) {
        if (!enabled || destination == null || limit <= 0) return List.of();
        int radius = Math.max(radiusMeters * 2, 9000);
        var coordinates = new DestinationCoordinates(destination.latitude(), destination.longitude());
        try {
            String filters;
            if (search != null && !search.isBlank()) {
                // Quote regex metacharacters and then encode as an Overpass string, never concatenate raw input.
                String escaped = search.trim().replaceAll("([\\\\.\\^$|?*+()\\[\\]{}])", "\\\\$1");
                filters = osmLines("\"name\"~" + objectMapper.writeValueAsString(escaped) + ",i", coordinates, radius);
            } else {
                filters = String.join("\n",
                        osmLines("\"railway\"=\"station\"", coordinates, radius),
                        osmLines("\"amenity\"=\"bus_station\"", coordinates, radius),
                        osmLines("\"place\"~\"neighbourhood|quarter|suburb|borough|square\"", coordinates, radius),
                        osmLines("\"tourism\"~\"attraction|museum\"", coordinates, radius),
                        osmLines("\"historic\"~\"monument|castle\"", coordinates, radius));
            }
            // Areas may be OSM relations; reuse exactly the same bounds for their lookup.
            String relations = filters.lines().filter(line -> line.startsWith("way[")).map(line -> "relation" + line.substring(3))
                    .collect(java.util.stream.Collectors.joining("\n"));
            String query = "[out:json][timeout:20];(" + filters + "\n" + relations + ");out center tags " + Math.max(limit, 60) + ";";
            JsonNode root = objectMapper.readTree(restClient.post().uri(endpoint).body(query).retrieve().body(String.class));
            // Overpass may return partial data with a timeout remark; do not present that as successful discovery.
            if (root.has("remark")) return List.of();
            List<com.journy.backend.startarea.StartAreaSuggestion> result = new ArrayList<>();
            for (JsonNode element : root.path("elements")) {
                JsonNode tags = element.path("tags");
                String label = tags.path("name").asText("");
                JsonNode center = element.has("lat") ? element : element.path("center");
                if (label.isBlank() || !center.path("lat").isNumber() || !center.path("lon").isNumber()) continue;
                double lat = center.path("lat").asDouble(), lon = center.path("lon").asDouble();
                if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180
                        || (lat == 0 && lon == 0) || distanceKm(destination.latitude(), destination.longitude(), lat, lon) > radius / 1000.0) continue;
                String osmType = element.path("type").asText("");
                if (!List.of("node", "way", "relation").contains(osmType) || !element.hasNonNull("id")) continue;
                String identity = osmType + "/" + element.path("id").asText();
                result.add(new com.journy.backend.startarea.StartAreaSuggestion("osm_" + identity.replace('/', '_'), label,
                        startAreaType(tags), lat, lon, "provider:osm", identity));
            }
            log.info("start_area_osm city={} query={} raw={} accepted={}", destination.locality(), search, root.path("elements").size(), result.size());
            return result;
        } catch (Exception exception) {
            log.warn("start_area_osm_failed city={} error={}", destination.locality(), exception.toString());
            return List.of();
        }
    }

    private String startAreaType(JsonNode tags) {
        if (tags.path("railway").asText().equals("station") || tags.path("amenity").asText().equals("bus_station")) return "transit_station";
        String place = tags.path("place").asText();
        if (place.equals("neighbourhood") || place.equals("quarter")) return "neighborhood";
        if (place.equals("suburb") || place.equals("borough")) return "district";
        if (place.equals("square")) return "square";
        if (tags.has("historic") || List.of("museum", "attraction").contains(tags.path("tourism").asText())) return "landmark";
        // A named hotel is a hotel, not evidence of a hotel-heavy area.
        return null;
    }

    private List<ExternalPlaceCandidate> fetch(ResolvedDestination destination, PlaceCategory category, int limit, String query, int radius) {
        try {
            String body = restClient.post()
                    .uri(endpoint)
                    .body(query)
                    .retrieve()
                    .body(String.class);
            return parse(destination, category, body, limit).stream()
                    .filter(place -> distanceKm(destination.latitude(), destination.longitude(),
                            place.latitude(), place.longitude()) <= radius / 1000.0)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("osm_request_failed city={} endpoint={} error={}", destination.locality(), endpoint, exception.toString());
            return List.of();
        }
    }

    private String query(ResolvedDestination destination, PlaceCategory category, int limit, int radius) {
        DestinationCoordinates coordinates = new DestinationCoordinates(destination.latitude(), destination.longitude());
        String filters = filtersFor(category, coordinates, radius);
        return """
                [out:json][timeout:20];
                (
                  %s
                );
                out center tags %d;
                """.formatted(filters, Math.max(limit, 8));
    }

    private String filtersFor(PlaceCategory category, DestinationCoordinates coordinates, int radius) {
        if (category == null) {
            return String.join("\n",
                    osmLines("\"tourism\"~\"museum|gallery|attraction|artwork|viewpoint\"", coordinates, radius),
                    osmLines("\"amenity\"~\"cafe|restaurant|food_court|bar|pub\"", coordinates, radius),
                    osmLines("\"leisure\"~\"park|garden\"", coordinates, radius),
                    osmLines("\"historic\"", coordinates, radius)
            );
        }
        return switch (category) {
            case COFFEE -> osmLines("\"amenity\"=\"cafe\"", coordinates, radius);
            case FOOD -> osmLines("\"amenity\"~\"restaurant|food_court|bar|pub\"", coordinates, radius);
            case CULTURE -> String.join("\n",
                    osmLines("\"tourism\"~\"museum|gallery|attraction|artwork\"", coordinates, radius),
                    osmLines("\"historic\"", coordinates, radius)
            );
            case FREE, WALKING -> String.join("\n",
                    osmLines("\"leisure\"~\"park|garden\"", coordinates, radius),
                    osmLines("\"tourism\"=\"viewpoint\"", coordinates, radius),
                    osmLines("\"historic\"", coordinates, radius)
            );
        };
    }

    private String osmLines(String filter, DestinationCoordinates coordinates, int radius) {
        // Bounding-box lookup avoids expensive Overpass around scans for broad discovery.
        // The response is still checked against the requested circular radius in fetch().
        double angularRadius = radius / 6371000.0;
        double latitudeDelta = Math.toDegrees(angularRadius);
        double longitudeDelta = Math.toDegrees(Math.asin(Math.min(1,
                Math.sin(angularRadius) / Math.cos(Math.toRadians(coordinates.latitude())))));
        String area;
        if (Math.abs(coordinates.latitude()) + latitudeDelta >= 90
                || Math.abs(coordinates.longitude()) + longitudeDelta >= 180) {
            area = String.format(Locale.ROOT, "(around:%d,%.6f,%.6f)", radius, coordinates.latitude(), coordinates.longitude());
        } else {
            area = String.format(Locale.ROOT, "(%.6f,%.6f,%.6f,%.6f)",
                    coordinates.latitude() - latitudeDelta, coordinates.longitude() - longitudeDelta,
                    coordinates.latitude() + latitudeDelta, coordinates.longitude() + longitudeDelta);
        }
        return "node[" + filter + "][name]" + area + ";\nway[" + filter + "][name]" + area + ";";
    }

    private List<ExternalPlaceCandidate> parse(ResolvedDestination destination, PlaceCategory requestedCategory, String body, int limit) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode elements = root.path("elements");
            if (root.has("remark")) {
                log.warn("osm_response_remark city={} remark={}", destination.locality(), root.path("remark").asText());
            }
            log.info("osm_response city={} elements={}", destination.locality(), elements.size());
            List<ExternalPlaceCandidate> places = new ArrayList<>();
            for (JsonNode element : elements) {
                JsonNode tags = element.path("tags");
                String name = tags.path("name").asText("");
                if (name.isBlank()) {
                    continue;
                }
                double latitude = element.has("lat") ? element.path("lat").asDouble() : element.path("center").path("lat").asDouble();
                double longitude = element.has("lon") ? element.path("lon").asDouble() : element.path("center").path("lon").asDouble();
                if (latitude == 0 || longitude == 0) {
                    continue;
                }
                if (distanceKm(destination.latitude(), destination.longitude(), latitude, longitude) > maxSearchRadiusKm()) {
                    continue;
                }
                PlaceCategory category = requestedCategory == null ? categoryFromTags(tags) : requestedCategory;
                String id = element.path("type").asText("node") + "/" + element.path("id").asText(slug(name));
                places.add(new ExternalPlaceCandidate(
                        name(),
                        id,
                        name,
                        destination.locality(),
                        category,
                        description(destination.locality(), category),
                        priceLevel(category),
                        0,
                        imageFor(tags, category),
                        address(tags, destination.locality()),
                        tags.path("website").asText(tags.path("contact:website").asText(null)),
                        latitude,
                        longitude,
                        tags.path("opening_hours").asText(defaultHours(category)),
                        duration(category),
                        tags(category)
                ));
                if (places.size() >= limit) {
                    break;
                }
            }
            return places;
        } catch (Exception exception) {
            log.warn("osm_parse_failed city={} error={}", destination.locality(), exception.toString());
            return List.of();
        }
    }

    private double maxSearchRadiusKm() {
        int largestRadiusMeters = searchRadii.stream().mapToInt(Integer::intValue).max().orElse(radiusMeters);
        return largestRadiusMeters / 1000.0;
    }

    private double distanceKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double earthRadiusKm = 6371.0;
        double latDelta = Math.toRadians(toLatitude - fromLatitude);
        double lonDelta = Math.toRadians(toLongitude - fromLongitude);
        double fromLat = Math.toRadians(fromLatitude);
        double toLat = Math.toRadians(toLatitude);
        double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(fromLat) * Math.cos(toLat)
                * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private PlaceCategory categoryFromTags(JsonNode tags) {
        String amenity = tags.path("amenity").asText("").toLowerCase(Locale.ROOT);
        String tourism = tags.path("tourism").asText("").toLowerCase(Locale.ROOT);
        if (amenity.contains("cafe")) return PlaceCategory.COFFEE;
        if (amenity.contains("restaurant") || amenity.contains("food") || amenity.contains("bar")) return PlaceCategory.FOOD;
        if (tourism.contains("museum") || tourism.contains("gallery") || tourism.contains("attraction")) return PlaceCategory.CULTURE;
        return PlaceCategory.WALKING;
    }

    private String description(String city, PlaceCategory category) {
        return switch (category) {
            case COFFEE -> "A real cafe candidate from OpenStreetMap that Journy can fit into your " + city + " route.";
            case FOOD -> "A real food stop candidate from OpenStreetMap for your " + city + " plan.";
            case CULTURE -> "A real culture place from OpenStreetMap that can anchor a " + city + " day.";
            case FREE, WALKING -> "A real walkable outdoor place from OpenStreetMap for a flexible " + city + " route.";
        };
    }

    private String address(JsonNode tags, String city) {
        String street = tags.path("addr:street").asText("");
        String number = tags.path("addr:housenumber").asText("");
        if (!street.isBlank()) {
            return (street + " " + number).trim();
        }
        return city + " local area";
    }

    private String imageFor(JsonNode tags, PlaceCategory category) {
        String directImage = tags.path("image").asText("");
        if (directImage.startsWith("http://") || directImage.startsWith("https://")) {
            return directImage;
        }

        String commons = tags.path("wikimedia_commons").asText("");
        if (!commons.isBlank()) {
            String fileName = commons.startsWith("File:") ? commons.substring("File:".length()) : commons;
            return "https://commons.wikimedia.org/wiki/Special:FilePath/" + urlEncode(fileName) + "?width=900";
        }

        return switch (category) {
            case COFFEE, FOOD, CULTURE, FREE, WALKING -> "";
        };
    }

    private String priceLevel(PlaceCategory category) {
        return category == PlaceCategory.FREE || category == PlaceCategory.WALKING ? "Free" : "Mid";
    }

    private String defaultHours(PlaceCategory category) {
        return switch (category) {
            case COFFEE -> "08:00 - 18:00";
            case FOOD -> "12:00 - 22:30";
            case CULTURE -> "10:00 - 18:00";
            case FREE, WALKING -> "Flexible route window";
        };
    }

    private int duration(PlaceCategory category) {
        return switch (category) {
            case FOOD -> 90;
            case CULTURE -> 120;
            case COFFEE -> 45;
            case FREE, WALKING -> 60;
        };
    }

    private String tags(PlaceCategory category) {
        return category.name().toLowerCase(Locale.ROOT) + ",provider:osm,real-place";
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
