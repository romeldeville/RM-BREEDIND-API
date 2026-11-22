package com.rmbreeding.api.alerts;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.FarmService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertsServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private FarmService farmService;
    @Mock
    private AuditService auditService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private AlertsService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AlertsService(alertRepository, taskRepository, farmService, auditService, eventPublisher);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // P11-01

    @Test
    void raisesNewAlert() {
        LocalDate due = LocalDate.parse("2026-03-10");
        when(alertRepository.findByTenantIdAndDedupKey(eq(tenantId),
                eq(AlertsService.dedupKey(farmId, "FARROWING_DUE", "cycle", null, due))))
                .thenReturn(Optional.empty());

        boolean created = service.raise(tenantId, farmId, "FARROWING_DUE", "CRITICAL", "Mise bas",
                "message", due, "cycle", null);

        assertThat(created).isTrue();
        verify(alertRepository).save(any(AlertEntity.class));
        verify(eventPublisher).publishEvent(any(AlertRaisedEvent.class));
    }

    @Test
    void deduplicatesAlertForSameDueDate() {
        LocalDate due = LocalDate.parse("2026-03-10");
        when(alertRepository.findByTenantIdAndDedupKey(eq(tenantId),
                eq(AlertsService.dedupKey(farmId, "FARROWING_DUE", "cycle", null, due))))
                .thenReturn(Optional.of(alert()));

        boolean created = service.raise(tenantId, farmId, "FARROWING_DUE", "CRITICAL", "Mise bas",
                "message", due, "cycle", null);

        assertThat(created).isFalse();
        verify(alertRepository, never()).save(any(AlertEntity.class));
    }

    @Test
    void listsAlertsFiltered() {
        when(alertRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(alert()));

        List<AlertsDtos.AlertResponse> result = service.list("OPEN", "CRITICAL", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo("FARROWING_DUE");
    }

    // P11-06

    @Test
    void closesAlert() {
        AlertEntity alert = alert();
        when(alertRepository.findByIdAndTenantId(alert.id, tenantId)).thenReturn(Optional.of(alert));

        AlertsDtos.AlertResponse response = service.close(alert.id);

        assertThat(response.status()).isEqualTo("CLOSED");
        verify(alertRepository).save(alert);
        verify(auditService).record(eq("ALERT_CLOSED"), eq("alert"), eq(alert.id));
    }

    @Test
    void rejectsClosingClosedAlert() {
        AlertEntity alert = alert();
        alert.status = "CLOSED";
        when(alertRepository.findByIdAndTenantId(alert.id, tenantId)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.close(alert.id)).isInstanceOf(ConflictException.class);
    }

    @Test
    void postponesAlert() {
        AlertEntity alert = alert();
        when(alertRepository.findByIdAndTenantId(alert.id, tenantId)).thenReturn(Optional.of(alert));

        AlertsDtos.AlertResponse response = service.postpone(alert.id,
                new AlertsDtos.PostponeRequest(LocalDate.parse("2026-04-01"), "Plus tard"));

        assertThat(response.status()).isEqualTo("POSTPONED");
        assertThat(response.dueDate()).isEqualTo(LocalDate.parse("2026-04-01"));
        assertThat(response.postponeReason()).isEqualTo("Plus tard");
    }

    @Test
    void cancelsAlert() {
        AlertEntity alert = alert();
        when(alertRepository.findByIdAndTenantId(alert.id, tenantId)).thenReturn(Optional.of(alert));

        AlertsDtos.AlertResponse response = service.cancel(alert.id, new AlertsDtos.CancelRequest("Erreur"));

        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    // P11-07

    @Test
    void autoClosesOpenAlertsForEntity() {
        UUID entityId = UUID.randomUUID();
        when(alertRepository.findByTenantIdAndEntityTypeAndEntityIdAndStatusIn(eq(tenantId), eq("cycle"),
                eq(entityId), any())).thenReturn(List.of(alert(), alert()));

        int closed = service.autoClose(tenantId, "cycle", entityId);

        assertThat(closed).isEqualTo(2);
        verify(alertRepository, times(2)).save(any(AlertEntity.class));
    }

    // P11-10

    @Test
    void returnsTodayAlertsAndTasks() {
        when(alertRepository.findByTenantIdAndStatusInAndDueDateLessThanEqualOrderByPriorityAscDueDateAsc(
                eq(tenantId), any(), any(LocalDate.class))).thenReturn(List.of(alert()));
        when(taskRepository.findByTenantIdAndStatusInAndDueDateLessThanEqualOrderByDueDateAsc(
                eq(tenantId), any(), any(LocalDate.class))).thenReturn(List.of(task()));

        AlertsDtos.TodayResponse today = service.today();

        assertThat(today.alerts()).hasSize(1);
        assertThat(today.tasks()).hasSize(1);
    }

    // P11-11

    @Test
    void createsAndCompletesTask() {
        when(farmService.list()).thenReturn(List.of(farm()));
        AlertsDtos.TaskResponse created = service.createTask(
                new AlertsDtos.TaskCreateRequest("Nettoyer", "Le bâtiment", LocalDate.parse("2026-03-10"), null, null));

        assertThat(created.status()).isEqualTo("OPEN");
        verify(taskRepository).save(any(TaskEntity.class));

        UUID taskId = created.id();
        TaskEntity entity = task();
        entity.id = taskId;
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(entity));

        AlertsDtos.TaskResponse completed = service.completeTask(taskId);

        assertThat(completed.status()).isEqualTo("COMPLETED");
    }

    @Test
    void listsTasks() {
        when(taskRepository.findByTenantIdOrderByDueDateAsc(tenantId)).thenReturn(List.of(task()));

        assertThat(service.listTasks()).hasSize(1);
    }

    @Test
    void listsOpenAlertsForDigest() {
        when(alertRepository.findByTenantIdAndStatusInOrderByDueDateAsc(eq(tenantId), any()))
                .thenReturn(List.of(alert(), alert()));

        List<AlertsDtos.AlertResponse> alerts = service.openAlerts();

        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).status()).isEqualTo("OPEN");
    }

    private AlertEntity alert() {
        AlertEntity alert = new AlertEntity();
        alert.id = UUID.randomUUID();
        alert.tenantId = tenantId;
        alert.farmId = farmId;
        alert.type = "FARROWING_DUE";
        alert.priority = "CRITICAL";
        alert.title = "Mise bas prévue";
        alert.dueDate = LocalDate.parse("2026-03-10");
        alert.status = "OPEN";
        alert.dedupKey = "key";
        alert.createdAt = Instant.now();
        return alert;
    }

    private TaskEntity task() {
        TaskEntity task = new TaskEntity();
        task.id = UUID.randomUUID();
        task.tenantId = tenantId;
        task.farmId = farmId;
        task.title = "Nettoyer";
        task.status = "OPEN";
        task.createdAt = Instant.now();
        return task;
    }

    private IdentityDtos.FarmResponse farm() {
        return new IdentityDtos.FarmResponse(farmId, tenantId, "Ferme", "F1", null, null, null);
    }
}
