package com.rmbreeding.api.alerts;

import com.rmbreeding.api.shared.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AlertsController {

    private final AlertsService alertsService;

    public AlertsController(AlertsService alertsService) {
        this.alertsService = alertsService;
    }

    // P11-01 / P11-10 Alerts

    @GetMapping("/alerts")
    public List<AlertsDtos.AlertResponse> listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) LocalDate dueBefore) {
        return alertsService.list(status, priority, dueBefore);
    }

    @GetMapping("/alerts/today")
    public AlertsDtos.TodayResponse today() {
        return alertsService.today();
    }

    // P11-06 Lifecycle

    @PostMapping("/alerts/{id}/close")
    public AlertsDtos.AlertResponse close(@PathVariable UUID id) {
        return alertsService.close(id);
    }

    @PostMapping("/alerts/{id}/postpone")
    public AlertsDtos.AlertResponse postpone(@PathVariable UUID id,
            @Valid @RequestBody AlertsDtos.PostponeRequest request) {
        return alertsService.postpone(id, request);
    }

    @PostMapping("/alerts/{id}/cancel")
    public AlertsDtos.AlertResponse cancel(@PathVariable UUID id,
            @Valid @RequestBody AlertsDtos.CancelRequest request) {
        return alertsService.cancel(id, request);
    }

    // P11-07 Automatic closure

    @PostMapping("/alerts/auto-close")
    public ResponseEntity<Integer> autoClose(@Valid @RequestBody AlertsDtos.AutoCloseRequest request) {
        int closed = alertsService.autoClose(TenantContext.requireTenantId(), request.entityType(),
                request.entityId());
        return ResponseEntity.status(HttpStatus.OK).body(closed);
    }

    // P11-11 Manual tasks

    @GetMapping("/tasks")
    public List<AlertsDtos.TaskResponse> listTasks() {
        return alertsService.listTasks();
    }

    @PostMapping("/tasks")
    public ResponseEntity<AlertsDtos.TaskResponse> createTask(
            @Valid @RequestBody AlertsDtos.TaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertsService.createTask(request));
    }

    @PostMapping("/tasks/{id}/complete")
    public AlertsDtos.TaskResponse completeTask(@PathVariable UUID id) {
        return alertsService.completeTask(id);
    }
}
