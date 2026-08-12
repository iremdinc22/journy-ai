package com.journy.backend.destination.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DestinationImageResolver {
    private final RestClient restClient;
    private final boolean wikipediaEnabled;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public DestinationImageResolver(
            RestClient.Builder restClientBuilder,
            @Value("${journy.destinations.wikipedia.enabled:true}") boolean wikipediaEnabled
    ) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "Journy/1.0 destination-image-resolver")
                .build();
        this.wikipediaEnabled = wikipediaEnabled;
    }

    public String imageFor(String city, String country) {
        String normalizedCity = city == null ? "" : city.trim();
        if (normalizedCity.isBlank()) {
            return fallbackImage("city");
        }
        String key = (normalizedCity + "|" + (country == null ? "" : country)).toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(key, ignored -> resolve(normalizedCity, country));
    }

    private String resolve(String city, String country) {
        String known = knownCityImage(city);
        if (known != null) {
            return known;
        }
        String wikipedia = wikipediaThumbnail(city);
        if (wikipedia != null) {
            return wikipedia;
        }
        return fallbackImage(city + "," + (country == null || country.isBlank() ? "city" : country) + ",city");
    }

    private String wikipediaThumbnail(String city) {
        if (!wikipediaEnabled) {
            return null;
        }
        try {
            String encodedTitle = URLEncoder.encode(city.trim().replace(' ', '_'), StandardCharsets.UTF_8).replace("+", "%20");
            JsonNode summary = restClient.get()
                    .uri("https://en.wikipedia.org/api/rest_v1/page/summary/" + encodedTitle)
                    .retrieve()
                    .body(JsonNode.class);
            String source = summary == null ? "" : summary.path("thumbnail").path("source").asText("");
            return source.isBlank() ? null : source.replaceFirst("/\\d+px-", "/900px-");
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String fallbackImage(String query) {
        return "https://loremflickr.com/900/600/" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String knownCityImage(String city) {
        return switch (normalize(city)) {
            case "milan", "milano" -> "https://images.unsplash.com/photo-1567760855784-589f09ed5dc6?auto=format&fit=crop&w=900&q=88";
            case "munich", "münchen" -> "https://images.unsplash.com/photo-1595867818082-083862f3d630?auto=format&fit=crop&w=900&q=88";
            case "brussels", "bruxelles", "brussel" -> "https://images.unsplash.com/photo-1491557345352-5929e343eb89?auto=format&fit=crop&w=900&q=88";
            case "budapest" -> "https://images.unsplash.com/photo-1549877452-9c387954fbc2?auto=format&fit=crop&w=900&q=88";
            case "zurich", "zürich" -> "https://images.unsplash.com/photo-1515488764276-beab7607c1e6?auto=format&fit=crop&w=900&q=88";
            case "stockholm" -> "https://images.unsplash.com/photo-1509356843151-3e7d96241e11?auto=format&fit=crop&w=900&q=88";
            case "oslo" -> "https://images.unsplash.com/photo-1513622470522-26c3c8a854bc?auto=format&fit=crop&w=900&q=88";
            case "athens", "atina" -> "https://images.unsplash.com/photo-1555993539-1732b0258235?auto=format&fit=crop&w=900&q=88";
            case "dublin" -> "https://images.unsplash.com/photo-1549918864-48ac978761a4?auto=format&fit=crop&w=900&q=88";
            case "canakkale" -> fallbackImage("Canakkale,Turkey,waterfront,city");
            case "edirne" -> "https://commons.wikimedia.org/wiki/Special:FilePath/Selimiye_Mosque_Edirne.jpg?width=900";
            case "bursa" -> "https://commons.wikimedia.org/wiki/Special:FilePath/Bursa_image.jpg?width=900";
            case "eskisehir" -> "https://commons.wikimedia.org/wiki/Special:FilePath/Porsuk_River_Eskisehir.jpg?width=900";
            case "ankara" -> "https://commons.wikimedia.org/wiki/Special:FilePath/An%C4%B1tkabir%2C_Ankara.jpg?width=900";
            case "izmir" -> "https://commons.wikimedia.org/wiki/Special:FilePath/Izmir_Kordon.jpg?width=900";
            case "antalya" -> "https://commons.wikimedia.org/wiki/Special:FilePath/Kaleici_Antalya.jpg?width=900";
            case "edinburgh", "edinburg" -> "https://commons.wikimedia.org/wiki/Special:FilePath/Edinburgh_Castle_31_July_2011.jpg?width=900";
            default -> null;
        };
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("ç", "c")
                .replace("ğ", "g")
                .replace("ı", "i")
                .replace("ö", "o")
                .replace("ş", "s")
                .replace("ü", "u");
    }
}
