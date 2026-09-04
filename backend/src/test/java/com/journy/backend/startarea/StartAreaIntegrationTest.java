package com.journy.backend.startarea;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.trip.mapper.TripMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:start_area;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StartAreaIntegrationTest {
    record City(String query, String locality, double lat, double lon, int id, String station, String area, String square) {}
    static final List<City> CITIES = List.of(
        new City("Edirne", "Edirne", 41.6771,26.5557,1,"Edirne Garı","Kaleiçi","Selimiye Meydanı"),
        new City("Sarajevo", "Sarajevo",43.8570713,18.4126147,2,"Željeznička stanica Sarajevo","Baščaršija","Trg oslobođenja"),
        new City("Ljubljana", "Ljubljana",46.0511,14.5051,3,"Ljubljana","Trnovo","Prešernov trg"),
        new City("Ghent", "Ghent",51.0543,3.7174,4,"Gent-Sint-Pieters","Patershol","Korenmarkt"),
        new City("Portland Maine", "Portland",43.6591,-70.2568,5,"Portland Transportation Center","East Bayside","Monument Square"),
        new City("Portland Oregon", "Portland",45.5152,-122.6784,6,"Portland Union Station","Pearl District","Pioneer Courthouse Square"));
    static final ObjectMapper JSON = new ObjectMapper();
    static final Map<Integer, AtomicInteger> CALLS = new ConcurrentHashMap<>();
    static final Set<String> RESOLVED = ConcurrentHashMap.newKeySet();
    static final HttpServer SERVER = server();
    @Autowired MockMvc mvc;
    @Autowired PlaceRepository places;
    @Autowired TripRepository trips;
    @Autowired TripMapper mapper;
    @DynamicPropertySource static void endpoints(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        r.add("journy.destinations.nominatim.enabled", () -> true);
        r.add("journy.destinations.nominatim.endpoint", () -> base + "/search");
        r.add("journy.places.osm.enabled", () -> true);
        r.add("journy.places.osm.endpoint", () -> base + "/interpreter");
    }
    @AfterAll static void stopServer() { SERVER.stop(0); }
    static Stream<City> cities() { return CITIES.stream(); }
    String register() throws Exception {
        return JSON.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("fullName","Start areas","email",UUID.randomUUID()+"@example.test","password","secret123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
    JsonNode search(String token, String destination, String query) throws Exception {
        return JSON.readTree(mvc.perform(get("/api/start-areas").param("destination",destination).param("query",query)
            .header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @ParameterizedTest @MethodSource("cities")
    void unseededDiscoveryIsGeographicTypedDeduplicatedAndCacheable(City c) throws Exception {
        String token = register();
        long placeCount = places.count();
        JsonNode result = search(token,c.query(),"");
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get(0).get("name").asText()).isEqualTo(c.station());
        assertThat(result.get(0).get("type").asText()).isEqualTo("transit_station");
        assertThat(result.get(1).get("name").asText()).isEqualTo(c.area());
        assertThat(result.get(2).get("name").asText()).isEqualTo(c.square());
        Set<String> ids = new HashSet<>();
        for (JsonNode item : result) {
            assertThat(ids.add(item.get("providerPlaceId").asText())).isTrue();
            assertThat(item.get("source").asText()).isEqualTo("provider:osm");
            assertThat(Math.abs(item.get("latitude").asDouble()-c.lat())).isLessThan(.02);
            assertThat(Math.abs(item.get("longitude").asDouble()-c.lon())).isLessThan(.02);
            assertThat(item.get("name").asText()).isNotIn(c.locality()+" city center",c.locality()+" main station",c.locality()+" old town");
        }
        int calls = CALLS.get(c.id()).get();
        assertThat(search(token,c.query(),"")).isEqualTo(result);
        assertThat(CALLS.get(c.id()).get()).isEqualTo(calls);
        assertThat(places.count()).isEqualTo(placeCount); // No Place seeding/pollution required for areas.
        if (!c.query().equals("Edirne")) assertThat(RESOLVED).contains(c.query());
    }
    @Test void destinationSearchRetainsSameNameLookupContextForTheMobileRequest() throws Exception {
        String token = register();
        JsonNode destinations = JSON.readTree(mvc.perform(get("/api/destinations").param("query", "Portland Oregon")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode selected = destinations.get(0);
        assertThat(selected.get("name").asText()).isEqualTo("Portland");
        assertThat(selected.get("lookupQuery").asText()).isEqualTo("Portland Oregon");
        assertThat(search(token, selected.get("lookupQuery").asText(), "").get(0).get("name").asText()).isEqualTo("Portland Union Station");
    }
    @Test void searchIsScopedAndZeroFailureAndSpecialCharactersNeverProduceFillers() throws Exception {
        String token = register();
        JsonNode local = search(token,"Sarajevo","Baščaršija");
        assertThat(local.size()).isEqualTo(1);
        assertThat(local.get(0).get("name").asText()).isEqualTo("Baščaršija");
        assertThat(search(token,"Edirne","Shinjuku").size()).isZero();
        assertThat(search(token,"Ljubljana","No matches").size()).isZero();
        assertThat(search(token,"Ljubljana","Provider failure").size()).isZero();
        assertThat(search(token,"Ljubljana","\".*(test)\\").size()).isZero();
    }
    @Test void selectedIdentityIsCanonicalPersistedAndHistoricalStringsRemainReadable() throws Exception {
        String token = register();
        JsonNode selection = search(token,"Edirne","").get(0);
        // Only planner fixtures are provided here; start-area discovery itself stays unseeded.
        for (int i=0;i<12;i++) places.save(com.journy.backend.support.VerifiedPlaceFixtures.place("start-plan-"+i,"Planner fixture "+i,"Edirne",com.journy.backend.place.enums.PlaceCategory.CULTURE));
        Map<String,Object> draft = new HashMap<>(Map.of("destination","Edirne","startingArea","client label",
            "startDate","2026-10-10","endDate","2026-10-11","travelerType","SOLO","budget","BALANCED","pace","BALANCED","interests",List.of("CULTURE")));
        var tampered = selection.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)tampered).put("latitude",35).put("longitude",139);
        draft.put("startingAreaSelection",tampered);
        JsonNode response = JSON.readTree(mvc.perform(post("/api/trips").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(draft))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(response.get("startingAreaSelection")).isEqualTo(selection);
        assertThat(response.get("startingArea").asText()).isEqualTo("Edirne Garı");
        var trip = trips.findById(response.get("id").asText()).orElseThrow();
        assertThat(trip.getStartingAreaProviderPlaceId()).isEqualTo(selection.get("providerPlaceId").asText());
        assertThat(trip.getStartingAreaLatitude()).isEqualTo(selection.get("latitude").asDouble());
        long count=trips.count();
        draft.put("startingAreaSelection",search(token,"Sarajevo","").get(0));
        mvc.perform(post("/api/trips").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(draft))).andExpect(status().isBadRequest());
        draft.remove("startingAreaSelection"); draft.put("startingArea","Shinjuku");
        mvc.perform(post("/api/trips").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(draft))).andExpect(status().isBadRequest());
        assertThat(trips.count()).isEqualTo(count);
        // Simulate an old string-only row without rewriting other historical data.
        trip.setStartingAreaId(null); trip.setStartingArea("Old manual start"); trips.save(trip);
        JsonNode old = JSON.readTree(mvc.perform(get("/api/trips/"+trip.getId()).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(old.get("startingArea").asText()).isEqualTo("Old manual start");
        assertThat(old.get("startingAreaSelection").isNull()).isTrue();
    }
    static Map<String,Object> element(int id, double lat, double lon, Map<String,String> tags) {
        return Map.of("type","node","id",id,"lat",lat,"lon",lon,"tags",tags);
    }
    static HttpServer server() {
        try {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/search",e->{
                String q=URLDecoder.decode(e.getRequestURI().getRawQuery().split("&q=",2)[1],StandardCharsets.UTF_8).replace('+',' ');
                City c=CITIES.stream().filter(v->v.query().equals(q)).findFirst().orElseThrow(); RESOLVED.add(q);
                byte[] body=JSON.writeValueAsBytes(List.of(Map.of("lat",c.lat(),"lon",c.lon(),"osm_type","relation","osm_id",c.id(),"address",Map.of("city",c.locality(),"country","Fixture country"))));
                e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();
            });
            server.createContext("/interpreter",e->{
                String q=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                var b=Pattern.compile("\\((-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+)\\)").matcher(q);
                if(!b.find() || q.contains("around:")) throw new IllegalStateException("Missing geographic bounding box");
                double lat=(Double.parseDouble(b.group(1))+Double.parseDouble(b.group(3)))/2;
                double lon=(Double.parseDouble(b.group(2))+Double.parseDouble(b.group(4)))/2;
                City c=CITIES.stream().filter(v->Math.abs(v.lat()-lat)<.00001 && Math.abs(v.lon()-lon)<.00001).findFirst().orElseThrow();
                CALLS.computeIfAbsent(c.id(),v->new AtomicInteger()).incrementAndGet();
                var first=element(c.id()*100,c.lat()+.01,c.lon(),Map.of("name",c.station(),"railway","station"));
                List<Object> elements=new ArrayList<>(List.of(first,first,
                    element(c.id()*100+1,c.lat()+.005,c.lon(),Map.of("name",c.area(),"place","neighbourhood")),
                    element(c.id()*100+2,c.lat(),c.lon(),Map.of("name",c.square(),"place","square")),
                    element(c.id()*100+3,35.6895,139.6917,Map.of("name","Shinjuku","railway","station")),
                    element(c.id()*100+4,0,0,Map.of("name","Invalid","railway","station")),
                    element(c.id()*100+5,c.lat(),c.lon(),Map.of("railway","station"))));
                if(q.contains("No matches") || q.contains("test")) elements.clear();
                int status=q.contains("Provider failure")?503:200;
                byte[] body=JSON.writeValueAsBytes(Map.of("elements",elements));
                e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,body.length);e.getResponseBody().write(body);e.close();
            });
            server.start();return server;
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
}
