package com.journy.backend.feedback.controller;

import com.journy.backend.feedback.dto.TasteFeedbackRequest;
import com.journy.backend.feedback.dto.TasteFeedbackResponse;
import com.journy.backend.feedback.service.TasteFeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/taste-feedback")
public class TasteFeedbackController {
    private final TasteFeedbackService tasteFeedbackService;

    public TasteFeedbackController(TasteFeedbackService tasteFeedbackService) {
        this.tasteFeedbackService = tasteFeedbackService;
    }

    @PostMapping
    public TasteFeedbackResponse record(@Valid @RequestBody TasteFeedbackRequest request) {
        return tasteFeedbackService.record(request);
    }
}
