package ru.mirea.cyberclub.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.mirea.cyberclub.backend.dto.AnalyticsResponse;
import ru.mirea.cyberclub.backend.service.AnalyticsService;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Аналитика")
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    @Operation(summary = "Сводные показатели клуба")
    public AnalyticsResponse summary() {
        return analytics.summary();
    }
}
