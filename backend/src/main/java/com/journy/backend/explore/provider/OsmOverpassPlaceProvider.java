package com.journy.backend.explore.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.destination.provider.DestinationCoordinates;
import com.journy.backend.place.enums.PlaceCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class OsmOverpassPlaceProvider implements PlaceProvider {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DestinationCoordinateResolver destinationCoordinateResolver;
    private final boolean enabled;
    private final String endpoint;
    private final int radiusMeters;
    private final List<Integer> searchRadii;

    public OsmOverpassPlaceProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            DestinationCoordinateResolver destinationCoordinateResolver,
            @Value("${journy.places.osm.enabled:true}") boolean enabled,
            @Value("${journy.places.osm.endpoint:https://overpass-api.de/api/interpreter}") String endpoint,
            @Value("${journy.places.osm.radius-meters:4500}") int radiusMeters
    ) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "Journy/1.0 place-provider")
                .requestFactory(requestFactory())
                .build();
        this.objectMapper = objectMapper;
        this.destinationCoordinateResolver = destinationCoordinateResolver;
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.radiusMeters = radiusMeters;
        this.searchRadii = List.of(radiusMeters, Math.max(radiusMeters * 2, 9000));
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(9));
        return factory;
    }

    @Override
    public String name() {
        return "osm";
    }

    @Override
    public List<ExternalPlaceCandidate> search(String city, PlaceCategory category, int limit) {
        if (!enabled || city == null || city.isBlank()) {
            return List.of();
        }
        for (int radius : searchRadii) {
            List<ExternalPlaceCandidate> candidates = fetch(city, category, limit, query(city, category, limit, radius));
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        return List.of();
    }

    private List<ExternalPlaceCandidate> fetch(String city, PlaceCategory category, int limit, String query) {
        try {
            String body = restClient.post()
                    .uri(endpoint)
                    .body(query)
                    .retrieve()
                    .body(String.class);
            return parse(city, category, body, limit);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private String query(String city, PlaceCategory category, int limit, int radius) {
        DestinationCoordinates coordinates = destinationCoordinateResolver.coordinatesFor(city);
        String filters = filtersFor(category, coordinates, radius);
        return """
                [out:json][timeout:8];
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
        String around = "(around:%d,%.6f,%.6f)".formatted(radius, coordinates.latitude(), coordinates.longitude());
        return "node[" + filter + "]" + around + ";\nway[" + filter + "]" + around + ";";
    }

    private List<ExternalPlaceCandidate> parse(String city, PlaceCategory requestedCategory, String body, int limit) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode elements = objectMapper.readTree(body).path("elements");
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
                PlaceCategory category = requestedCategory == null ? categoryFromTags(tags) : requestedCategory;
                String id = element.path("type").asText("node") + "/" + element.path("id").asText(slug(name));
                places.add(new ExternalPlaceCandidate(
                        name(),
                        id,
                        name,
                        city,
                        category,
                        description(city, category),
                        priceLevel(category),
                        syntheticRating(name, category),
                        imageFor(tags, category),
                        address(tags, city),
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
            return List.of();
        }
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
            case COFFEE -> "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=85";
            case FOOD -> "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=900&q=85";
            case CULTURE -> "https://images.unsplash.com/photo-1518998053901-5348d3961a04?auto=format&fit=crop&w=900&q=85";
            case FREE, WALKING -> "https://images.unsplash.com/photo-1516834474-48c0abc2a902?auto=format&fit=crop&w=900&q=85";
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

    private double syntheticRating(String name, PlaceCategory category) {
        int seed = Math.abs((name + category.name()).hashCode() % 5);
        return Math.round((4.35 + seed * 0.08) * 10.0) / 10.0;
    }

    private String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
