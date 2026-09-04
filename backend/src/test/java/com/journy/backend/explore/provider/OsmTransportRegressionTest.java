package com.journy.backend.explore.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.destination.provider.ResolvedDestination;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class OsmTransportRegressionTest {
    final ResolvedDestination destination = new ResolvedDestination("Tallinn", "Tallinn", "Tallinn", "", "Estonia", 59.437242, 24.7572693, "nominatim", "relation/1");
    final String payload = "{\"elements\":[{\"type\":\"node\",\"id\":1,\"lat\":59.437242,\"lon\":24.7572693,\"tags\":{\"name\":\"Named fixture\",\"tourism\":\"museum\"}}]}";

    @Test void transportTriesSecondDnsAddressWhenFirstConnectionIsRefused() throws Exception {
        HttpServer server = server(200, payload, 0);
        var manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(new DnsResolver() {
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return new InetAddress[]{InetAddress.getByName("127.0.0.2"), InetAddress.getByName("127.0.0.1")};
            }
            public String resolveCanonicalHostname(String host) { return host; }
        }).build();
        try (var client = HttpClients.custom().setConnectionManager(manager).build()) {
            var provider = provider("http://fixture.test:" + server.getAddress().getPort());
            var factory = factory(provider);
            factory.setHttpClient(client); // Production transport/timeouts; deterministic multi-address DNS only.
            useFactory(provider, factory);
            assertThat(provider.search(destination, null, 8)).extracting(ExternalPlaceCandidate::name).containsExactly("Named fixture");
        } finally { server.stop(0); }
    }

    @Test void httpProviderErrorProducesNoSyntheticCandidates() throws Exception {
        HttpServer server = server(503, "Provider unavailable", 0);
        try { assertThat(provider("http://127.0.0.1:" + server.getAddress().getPort()).search(destination, null, 8)).isEmpty(); }
        finally { server.stop(0); }
    }

    @Test void readTimeoutProducesNoSyntheticCandidates() throws Exception {
        HttpServer server = server(200, payload, 250);
        try {
            var provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            var factory = factory(provider);
            factory.setReadTimeout(Duration.ofMillis(30)); // Exercise timeout without a 46-second test.
            useFactory(provider, factory);
            assertThat(provider.search(destination, null, 8)).isEmpty();
        } finally { server.stop(0); }
    }
    OsmOverpassPlaceProvider provider(String url) {
        return new OsmOverpassPlaceProvider(RestClient.builder(), new ObjectMapper(), true, url, 4500);
    }
    HttpComponentsClientHttpRequestFactory factory(OsmOverpassPlaceProvider provider) throws Exception {
        var method = OsmOverpassPlaceProvider.class.getDeclaredMethod("requestFactory");
        method.setAccessible(true);
        return (HttpComponentsClientHttpRequestFactory) method.invoke(provider);
    }
    void useFactory(OsmOverpassPlaceProvider provider, HttpComponentsClientHttpRequestFactory factory) throws Exception {
        var field = OsmOverpassPlaceProvider.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(provider, RestClient.builder().requestFactory(factory).build());
    }
    HttpServer server(int status, String payload, long delay) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", e -> {
            e.getRequestBody().readAllBytes();
            if (delay > 0) try { Thread.sleep(delay); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            try {
                byte[] body = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                e.sendResponseHeaders(status, body.length); e.getResponseBody().write(body);
            } finally { e.close(); }
        });
        server.start(); return server;
    }
}
