package com.rmbreeding.api.alerts;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.identity.FarmService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AlertsService {

    static final Set<String> OPEN_STATUSES = Set.of("OPEN", "POSTPONED");
    static final Set<String> FINAL_STATUSES = Set.of("CLOSED", "CANCELLED");

    private final AlertRepository alertRepository;
    private final TaskRepository taskRepository;
    private final FarmService farmService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public AlertsService(AlertRepository alertRepository,
            TaskRepository taskRepository,
            FarmService farmService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.alertRepository = alertRepository;
        this.taskRepository = taskRepository;
        this.farmService = farmService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    // P11-01 Alert model and deduplication

    @Transactional
    public boolean raise(UUID tenantId, UUID farmId, String type, String priority, String title,
            String message, LocalDate dueDate, String entityType, UUID entityId) {
        String dedupKey = dedupKey(farmId, type, entityType, entityId, dueDate);
        if (alertRepository.findByTenantIdAndDedupKey(tenantId, dedupKey).isPresent()) {
            return false;
        }
        AlertEntity alert = new AlertEntity();
        alert.id = UUID.randomUUID();
        alert.tenantId = tenantId;
        alert.farmId = farmId;
        alert.type = type;
        alert.priority = priority;
        alert.title = title;
        alert.message = message;
        alert.dueDate = dueDate;
        alert.entityType = entityType;
        alert.entityId = entityId;
        alert.status = "OPEN";
        alert.dedupKey = dedupKey;
        alert.createdAt = Instant.now();
        alertRepository.save(alert);
        eventPublisher.publishEvent(new AlertRaisedEvent(tenantId, farmId, type, priority, title, message,
                dueDate, entityType, entityId));
        return true;
    }

    static String dedupKey(UUID farmId, String type, String entityType, UUID entityId, LocalDate dueDate) {
        return (farmId == null ? "-" : farmId.toString())
                + "|" + type
                + "|" + (entityType == null ? "-" : entityType)
                + "|" + (entityId == null ? "-" : entityId.toString())
                + "|" + (dueDate == null ? "-" : dueDate.toString());
    }

    public List<AlertsDtos.AlertResponse> list(String status, String priority, LocalDate dueBefore) {
        UUID tenantId = TenantContext.requireTenantId();
        Specification<AlertEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(builder.equal(root.get("priority"), priority));
            }
            if (dueBefore != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("dueDate"), dueBefore));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return alertRepository.findAll(specification,
                        org.springframework.data.domain.Sort.by("dueDate").ascending()).stream()
                .map(this::toAlertResponse)
                .toList();
    }

    // P11-06 Lifecycle: close, postpone, cancel

    @Transactional
    public AlertsDtos.AlertResponse close(UUID alertId) {
        UUID tenantId = TenantContext.requireTenantId();
        AlertEntity alert = requireAlert(alertId, tenantId);
        if (FINAL_STATUSES.contains(alert.status)) {
            throw new ConflictException("Alert is already " + alert.status);
        }
        alert.status = "CLOSED";
        alert.closedAt = Instant.now();
        alert.closedBy = AuthContext.userId();
        alertRepository.save(alert);
        auditService.record("ALERT_CLOSED", "alert", alert.id);
        return toAlertResponse(alert);
    }

    @Transactional
    public AlertsDtos.AlertResponse postpone(UUID alertId, AlertsDtos.PostponeRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        AlertEntity alert = requireAlert(alertId, tenantId);
        if (FINAL_STATUSES.contains(alert.status)) {
            throw new ConflictException("Alert is already " + alert.status);
        }
        alert.dueDate = request.dueDate();
        alert.postponeReason = request.reason();
        alert.status = "POSTPONED";
        alertRepository.save(alert);
        auditService.record("ALERT_POSTPONED", "alert", alert.id);
        return toAlertResponse(alert);
    }

    @Transactional
    public AlertsDtos.AlertResponse cancel(UUID alertId, AlertsDtos.CancelRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        AlertEntity alert = requireAlert(alertId, tenantId);
        if (FINAL_STATUSES.contains(alert.status)) {
            throw new ConflictException("Alert is already " + alert.status);
        }
        alert.status = "CANCELLED";
        alert.postponeReason = request.reason();
        alert.closedAt = Instant.now();
        alert.closedBy = AuthContext.userId();
        alertRepository.save(alert);
        auditService.record("ALERT_CANCELLED", "alert", alert.id);
        return toAlertResponse(alert);
    }

    // P11-07 Automatic closure by business event

    @Transactional
    public int autoClose(UUID tenantId, String entityType, UUID entityId) {
        List<AlertEntity> alerts = alertRepository
                .findByTenantIdAndEntityTypeAndEntityIdAndStatusIn(tenantId, entityType, entityId, OPEN_STATUSES);
        Instant now = Instant.now();
        for (AlertEntity alert : alerts) {
            alert.status = "CLOSED";
            alert.closedAt = now;
            alert.closedBy = AuthContext.userId();
            alertRepository.save(alert);
            auditService.record("ALERT_AUTO_CLOSED", "alert", alert.id);
        }
        return alerts.size();
    }

    // P11-10 Today view

    public AlertsDtos.TodayResponse today() {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate today = LocalDate.now();
        List<AlertsDtos.AlertResponse> alerts = alertRepository
                .findByTenantIdAndStatusInAndDueDateLessThanEqualOrderByPriorityAscDueDateAsc(
                        tenantId, OPEN_STATUSES, today).stream()
                .map(this::toAlertResponse)
                .toList();
        List<AlertsDtos.TaskResponse> tasks = taskRepository
                .findByTenantIdAndStatusInAndDueDateLessThanEqualOrderByDueDateAsc(tenantId, OPEN_STATUSES, today)
                .stream()
                .map(this::toTaskResponse)
                .toList();
        return new AlertsDtos.TodayResponse(alerts, tasks);
    }

    // P11-11 Manual tasks

    public List<AlertsDtos.TaskResponse> listTasks() {
        UUID tenantId = TenantContext.requireTenantId();
        return taskRepository.findByTenantIdOrderByDueDateAsc(tenantId).stream()
                .map(this::toTaskResponse)
                .toList();
    }

    @Transactional
    public AlertsDtos.TaskResponse createTask(AlertsDtos.TaskCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        TaskEntity task = new TaskEntity();
        task.id = UUID.randomUUID();
        task.tenantId = tenantId;
        task.farmId = requireDefaultFarmId();
        task.title = request.title();
        task.description = request.description();
        task.dueDate = request.dueDate();
        task.assignedTo = request.assignedTo();
        task.recurrence = request.recurrence();
        task.status = "OPEN";
        task.createdAt = Instant.now();
        taskRepository.save(task);
        auditService.record("TASK_CREATED", "task", task.id);
        return toTaskResponse(task);
    }

    @Transactional
    public AlertsDtos.TaskResponse completeTask(UUID taskId) {
        UUID tenantId = TenantContext.requireTenantId();
        TaskEntity task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if ("COMPLETED".equals(task.status)) {
            throw new ConflictException("Task is already completed");
        }
        task.status = "COMPLETED";
        task.completedAt = Instant.now();
        taskRepository.save(task);
        auditService.record("TASK_COMPLETED", "task", task.id);
        return toTaskResponse(task);
    }

    // Helpers

    public long openAlertCount() {
        UUID tenantId = TenantContext.requireTenantId();
        return alertRepository.countByTenantIdAndStatus(tenantId, "OPEN")
                + alertRepository.countByTenantIdAndStatus(tenantId, "POSTPONED");
    }

    /**
     * Open alerts for the current tenant, used by the e-mail digest. Unlike
     * {@link #list(String, String, LocalDate)} it has no request filters.
     */
    public List<AlertsDtos.AlertResponse> openAlerts() {
        UUID tenantId = TenantContext.requireTenantId();
        return alertRepository.findByTenantIdAndStatusInOrderByDueDateAsc(tenantId, OPEN_STATUSES).stream()
                .map(this::toAlertResponse)
                .toList();
    }

    private AlertEntity requireAlert(UUID alertId, UUID tenantId) {
        return alertRepository.findByIdAndTenantId(alertId, tenantId)
                .orElseThrow(() -> new NotFoundException("Alert not found"));
    }

    UUID requireDefaultFarmId() {
        List<IdentityDtos.FarmResponse> farms = farmService.list();
        if (farms.isEmpty()) {
            throw new NotFoundException("No farm available for this tenant");
        }
        return farms.get(0).id();
    }

    private AlertsDtos.AlertResponse toAlertResponse(AlertEntity alert) {
        return new AlertsDtos.AlertResponse(alert.id, alert.farmId, alert.type, alert.priority, alert.title,
                alert.message, alert.dueDate, alert.status, alert.entityType, alert.entityId,
                alert.assignedRole, alert.postponeReason, alert.createdAt, alert.closedAt);
    }

    private AlertsDtos.TaskResponse toTaskResponse(TaskEntity task) {
        return new AlertsDtos.TaskResponse(task.id, task.farmId, task.title, task.description, task.dueDate,
                task.assignedTo, task.recurrence, task.status, task.createdAt, task.completedAt);
    }
}
