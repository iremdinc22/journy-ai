package com.journy.backend.profile.controller;

import com.journy.backend.profile.dto.ProfileResponse;
import com.journy.backend.profile.dto.UpdatePreferencesRequest;
import com.journy.backend.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ProfileResponse me() {
        return profileService.me();
    }

    @PutMapping("/me/preferences")
    public ProfileResponse updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        return profileService.updatePreferences(request);
    }
}
