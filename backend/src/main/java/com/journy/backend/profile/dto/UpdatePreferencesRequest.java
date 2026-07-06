package com.journy.backend.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdatePreferencesRequest(
        @NotBlank String defaultPace,
        @NotBlank String defaultBudget,
        @NotBlank String foodDiscovery,
        @NotNull Boolean planChangeNotifications,
        @NotNull Boolean foodWindowNotifications
) {
}
