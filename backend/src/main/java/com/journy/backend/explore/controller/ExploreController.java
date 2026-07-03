package com.journy.backend.explore.controller;

import com.journy.backend.explore.dto.PlaceResponse;
import com.journy.backend.explore.service.ExploreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/explore")
public class ExploreController {
    private final ExploreService exploreService;

    public ExploreController(ExploreService exploreService) {
        this.exploreService = exploreService;
    }

    @GetMapping("/places")
    public List<PlaceResponse> places(@RequestParam(required = false) String category) {
        return exploreService.places(category);
    }
}
