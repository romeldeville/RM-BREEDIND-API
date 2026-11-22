package com.rmbreeding.api.reporting;

import com.rmbreeding.api.alerts.AlertsDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportingControllerTest {

    @Mock
    private IndicatorService indicatorService;
    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    private final LocalDate from = LocalDate.parse("2026-11-01");
    private final LocalDate to = LocalDate.parse("2026-11-30");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportingController(indicatorService, dashboardService))
                .build();
    }

    @Test
    void exposesDashboard() throws Exception {
        AlertsDtos.AlertResponse alert = new AlertsDtos.AlertResponse(UUID.randomUUID(), null,
                "LOW_STOCK", "CRITICAL", "Stock bas", "message", LocalDate.now(), "OPEN", "stock",
                null, null, null, Instant.now(), null);
        when(dashboardService.dashboard()).thenReturn(new ReportingDtos.DashboardResponse(12, 3, 2,
                new BigDecimal("0.75"), new BigDecimal("1200"), List.of(alert)));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.herdSize").value(12))
                .andExpect(jsonPath("$.openAlerts").value(3))
                .andExpect(jsonPath("$.birthsThisMonth").value(2))
                .andExpect(jsonPath("$.averageDailyGain").value(0.75))
                .andExpect(jsonPath("$.grossMargin").value(1200))
                .andExpect(jsonPath("$.criticalAlerts[0].type").value("LOW_STOCK"));
    }

    @Test
    void exposesUnavailableDashboardValuesAsNull() throws Exception {
        when(dashboardService.dashboard()).thenReturn(new ReportingDtos.DashboardResponse(0, 0, 0,
                null, null, List.of()));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageDailyGain").doesNotExist())
                .andExpect(jsonPath("$.grossMargin").doesNotExist())
                .andExpect(jsonPath("$.criticalAlerts").isEmpty());
    }

    @Test
    void listsIndicatorsForPeriod() throws Exception {
        when(indicatorService.list(from, to)).thenReturn(List.of(
                new ReportingDtos.IndicatorResponse("HERD_SIZE", "Effectif total", "tête",
                        new BigDecimal("42"), from, to),
                new ReportingDtos.IndicatorResponse("PREGNANCY_RATE", "Taux de gestation", "%",
                        null, from, to)));

        mockMvc.perform(get("/api/v1/indicators")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("HERD_SIZE"))
                .andExpect(jsonPath("$[0].value").value(42))
                .andExpect(jsonPath("$[1].code").value("PREGNANCY_RATE"))
                .andExpect(jsonPath("$[1].value").doesNotExist());
    }

    @Test
    void exposesSingleIndicator() throws Exception {
        when(indicatorService.get("HERD_SIZE", from, to)).thenReturn(
                new ReportingDtos.IndicatorResponse("HERD_SIZE", "Effectif total", "tête",
                        new BigDecimal("42"), from, to));

        mockMvc.perform(get("/api/v1/indicators/HERD_SIZE")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("HERD_SIZE"))
                .andExpect(jsonPath("$.label").value("Effectif total"))
                .andExpect(jsonPath("$.value").value(42));
    }
}
