package com.journy.backend.startarea;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/start-areas")
public class StartAreaController {
    private final StartAreaSuggestionService suggestions;
    public StartAreaController(StartAreaSuggestionService suggestions) { this.suggestions = suggestions; }
    @GetMapping
    public List<StartAreaSuggestion> search(@RequestParam String destination, @RequestParam(required = false) String query) {
        return suggestions.suggestions(destination, query);
    }
}
