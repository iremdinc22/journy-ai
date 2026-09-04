package com.journy.backend.explore.search;

import com.journy.backend.explore.dto.PlaceResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/explore/places/search")
public class PlaceSearchController {
    private final PlaceSearchService search;
    public PlaceSearchController(PlaceSearchService search) { this.search = search; }
    @GetMapping
    public List<PlaceResponse> search(@RequestParam String city, @RequestParam String q) {
        return search.search(city, q);
    }
}
