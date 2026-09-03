package com.journy.backend.explore.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.place.enums.PlaceCategory;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OsmOverpassPlaceProviderTest {

    @Test
    void buildsOverpassQueryWithResolvedDestinationCoordinates() throws Exception {
        OsmOverpassPlaceProvider provider = new OsmOverpassPlaceProvider(
                RestClient.builder(),
                new ObjectMapper(),
                true,
                "http://example.test/api/interpreter",
                4500
        );
        ResolvedDestination destination = lasVegas();

        java.lang.reflect.Method query = OsmOverpassPlaceProvider.class
                .getDeclaredMethod("query", ResolvedDestination.class, PlaceCategory.class, int.class, int.class);
        query.setAccessible(true);

        String body = (String) query.invoke(provider, destination, PlaceCategory.CULTURE, 8, 4500);

        assertThat(body).contains("[name]").doesNotContain("around:");
        java.util.regex.Matcher bounds = java.util.regex.Pattern
                .compile("\\((-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+)\\)").matcher(body);
        assertThat(bounds.find()).isTrue();
        assertThat(Double.parseDouble(bounds.group(1))).isLessThan(destination.latitude());
        assertThat(Double.parseDouble(bounds.group(2))).isLessThan(destination.longitude());
        assertThat(Double.parseDouble(bounds.group(3))).isGreaterThan(destination.latitude());
        assertThat(Double.parseDouble(bounds.group(4))).isGreaterThan(destination.longitude());
    }

    @Test
    @SuppressWarnings("unchecked")
    void normalizesOsmElementsAgainstResolvedDestination() throws Exception {
        OsmOverpassPlaceProvider provider = new OsmOverpassPlaceProvider(
                RestClient.builder(),
                new ObjectMapper(),
                true,
                "http://example.test/api/interpreter",
                4500
        );
        java.lang.reflect.Method parse = OsmOverpassPlaceProvider.class
                .getDeclaredMethod("parse", ResolvedDestination.class, PlaceCategory.class, String.class, int.class);
        parse.setAccessible(true);
        String response = """
                {"elements":[
                  {"type":"node","id":100,"lat":36.1699,"lon":-115.1398,"tags":{"name":"Las Vegas Arts District","tourism":"attraction"}},
                  {"type":"node","id":101,"lat":52.3676,"lon":4.9041,"tags":{"name":"Wrong City","tourism":"attraction"}}
                ]}
                """;

        List<ExternalPlaceCandidate> places = (List<ExternalPlaceCandidate>) parse.invoke(provider, lasVegas(), PlaceCategory.CULTURE, response, 8);

        assertThat(places).singleElement().satisfies(place -> {
            assertThat(place.name()).isEqualTo("Las Vegas Arts District");
            assertThat(place.city()).isEqualTo("Las Vegas");
            assertThat(place.provider()).isEqualTo("osm");
            assertThat(place.providerPlaceId()).isEqualTo("node/100");
            assertThat(place.category()).isEqualTo(PlaceCategory.CULTURE);
            assertThat(place.latitude()).isEqualTo(36.1699);
            assertThat(place.longitude()).isEqualTo(-115.1398);
        });
    }

    @Test
    void httpDiscoveryRejectsBoundingBoxCornersOutsideCircularRadius() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/interpreter", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = """
                    {"elements":[
                      {"type":"node","id":100,"lat":36.1699,"lon":-115.1398,"tags":{"name":"Inside","tourism":"museum"}},
                      {"type":"node","id":101,"lat":36.203,"lon":-115.1,"tags":{"name":"Outside circle","tourism":"museum"}}
                    ]}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provider = new OsmOverpassPlaceProvider(RestClient.builder(), new ObjectMapper(), true,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/interpreter", 4500);
            assertThat(provider.search(lasVegas(), null, 20)).extracting(ExternalPlaceCandidate::name)
                    .containsExactly("Inside");
        } finally {
            server.stop(0);
        }
    }

    private ResolvedDestination lasVegas() {
        return new ResolvedDestination(
                "Las Vegas",
                "Las Vegas, Nevada, United States",
                "Las Vegas",
                "Nevada",
                "United States",
                36.1673,
                -115.1492,
                "nominatim",
                "relation/170131"
        );
    }
}
