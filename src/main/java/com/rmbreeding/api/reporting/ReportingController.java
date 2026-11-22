package com.rmbreeding.api.reporting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReportingController {

    private final IndicatorService indicatorService;
    private final DashboardService dashboardService;

    public ReportingController(IndicatorService indicatorService, DashboardService dashboardService) {
        this.indicatorService = indicatorService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/indicators")
    public List<ReportingDtos.IndicatorResponse> listIndicators(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) UUID speciesId) {
        return indicatorService.list(from, to);
    }

    @GetMapping("/indicators/{code}")
    public ReportingDtos.IndicatorResponse getIndicator(
            @PathVariable String code,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return indicatorService.get(code, from, to);
    }

    @GetMapping("/dashboard")
    public ReportingDtos.DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }
}
