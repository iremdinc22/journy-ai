package com.journy.backend.destination.controller;

import com.journy.backend.destination.dto.DestinationResponse;
import com.journy.backend.destination.service.DestinationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/destinations")
public class DestinationController {
    private final DestinationService destinationService;

    public DestinationController(DestinationService destinationService) {
        this.destinationService = destinationService;
    }

    @GetMapping
    public List<DestinationResponse> search(@RequestParam(required = false) String query) {
        return destinationService.search(query);
    }

    @GetMapping("/popular")
    public List<DestinationResponse> popular() {
        return destinationService.popular();
    }

    @GetMapping("/{id}")
    public DestinationResponse detail(@PathVariable String id) {
        return destinationService.detail(id);
    }
}
