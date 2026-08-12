package com.journy.backend.destination.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class NominatimDestinationProvider implements DestinationProvider {
    private final RestClient restClient;
    private final boolean enabled;
    private final String endpoint;

    public NominatimDestinationProvider(
            RestClient.Builder restClientBuilder,
            @Value("${journy.destinations.nominatim.enabled:true}") boolean enabled,
            @Value("${journy.destinations.nominatim.endpoint:https://nominatim.openstreetmap.org/search}") String endpoint
    ) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "Journy/1.0 destination-provider")
                .build();
        this.enabled = enabled;
        this.endpoint = endpoint;
    }

    @Override
    public String name() {
        return "nominatim";
    }

    @Override
    public Optional<DestinationCandidate> resolve(String query) {
        if (!enabled || query == null || query.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode results = restClient.get()
                    .uri(endpoint + "?format=jsonv2&addressdetails=1&limit=1&q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8))
                    .retrieve()
                    .body(JsonNode.class);
            if (results == null || !results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            JsonNode result = results.get(0);
            JsonNode address = result.path("address");
            String city = firstNonBlank(
                    address.path("city").asText(""),
                    address.path("town").asText(""),
                    address.path("municipality").asText(""),
                    address.path("county").asText(""),
                    query.trim()
            );
            String country = firstNonBlank(address.path("country").asText(""), "Provider-backed");
            return Optional.of(new DestinationCandidate(
                    name(),
                    result.path("osm_type").asText("place") + "/" + result.path("osm_id").asText(slug(city)),
                    city,
                    country,
                    result.path("display_name").asText(city + ", " + country),
                    result.path("lat").asDouble(),
                    result.path("lon").asDouble()
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String slug(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
