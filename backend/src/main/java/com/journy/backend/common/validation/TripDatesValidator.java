package com.journy.backend.common.validation;

import com.journy.backend.trip.dto.CreateTripRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TripDatesValidator implements ConstraintValidator<ValidTripDates, CreateTripRequest> {
    @Override
    public boolean isValid(CreateTripRequest request, ConstraintValidatorContext context) {
        if (request == null || request.startDate() == null || request.endDate() == null) {
            return true;
        }
        return request.endDate().isAfter(request.startDate());
    }
}
