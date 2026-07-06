package com.journy.backend.savedplace.controller;

import com.journy.backend.savedplace.dto.SavedPlaceRequest;
import com.journy.backend.savedplace.dto.SavedPlaceResponse;
import com.journy.backend.savedplace.dto.SavedPlaceStatusResponse;
import com.journy.backend.savedplace.service.SavedPlaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/saved-places")
public class SavedPlaceController {
    private final SavedPlaceService savedPlaceService;

    public SavedPlaceController(SavedPlaceService savedPlaceService) {
        this.savedPlaceService = savedPlaceService;
    }

    @GetMapping
    public List<SavedPlaceResponse> list() {
        return savedPlaceService.list();
    }

    @GetMapping("/{placeId}/status")
    public SavedPlaceStatusResponse status(@PathVariable String placeId) {
        return savedPlaceService.status(placeId);
    }

    @PostMapping
    public SavedPlaceResponse save(@Valid @RequestBody SavedPlaceRequest request) {
        return savedPlaceService.save(request);
    }

    @DeleteMapping("/{placeId}")
    public void remove(@PathVariable String placeId) {
        savedPlaceService.remove(placeId);
    }
}
