package com.rmbreeding.api.referential;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeciesServiceTest {

    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private SpeciesTemplateRepository templateRepository;
    @Mock
    private BreedRepository breedRepository;
    @Mock
    private VaccinationPlanRepository vaccinationPlanRepository;
    @Mock
    private FeedingPlanRepository feedingPlanRepository;
    @Mock
    private GrowthCurveRepository growthCurveRepository;
    @Mock
    private AuditService auditService;

    private SpeciesService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SpeciesService(speciesRepository, templateRepository, breedRepository,
                vaccinationPlanRepository, feedingPlanRepository, growthCurveRepository, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsSpeciesWithDefaults() {
        when(speciesRepository.existsByTenantIdAndCode(tenantId, "bovin")).thenReturn(false);

        ReferentialDtos.SpeciesResponse response = service.create(new ReferentialDtos.SpeciesRequest(
                "Bovin", "bovin", "MILK", "INDIVIDUAL", 283, 21, 90, null,
                Map.of("lactationDays", 305)));

        assertThat(response.name()).isEqualTo("Bovin");
        assertThat(response.settings()).containsEntry("lactationDays", 305);
        verify(speciesRepository).save(any(SpeciesEntity.class));
        verify(auditService).record("SPECIES_CREATED", "species", response.id());
    }

    @Test
    void rejectsDuplicateCode() {
        when(speciesRepository.existsByTenantIdAndCode(tenantId, "bovin")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ReferentialDtos.SpeciesRequest(
                "Bovin", "bovin", "MILK", "INDIVIDUAL", null, null, null, null, Map.of())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void archivesSpeciesOnce() {
        SpeciesEntity entity = new SpeciesEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        when(speciesRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        service.archive(entity.id);
        service.archive(entity.id);

        assertThat(entity.archivedAt).isNotNull();
        verify(auditService).record("SPECIES_ARCHIVED", "species", entity.id);
    }

    @Test
    void unknownSpeciesIsNotFound() {
        UUID id = UUID.randomUUID();
        when(speciesRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void patchesSpeciesFields() {
        SpeciesEntity entity = existingSpecies();
        when(speciesRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        ReferentialDtos.SpeciesResponse response = service.patch(entity.id,
                new ReferentialDtos.SpeciesPatchRequest("Bovin laitier", "MILK", null, 285, null, null, null, null));

        assertThat(response.name()).isEqualTo("Bovin laitier");
        assertThat(response.productionType()).isEqualTo("MILK");
        assertThat(response.gestationDays()).isEqualTo(285);
        verify(auditService).record("SPECIES_UPDATED", "species", entity.id);
    }

    @Test
    void createsTemplate() {
        SpeciesEntity entity = existingSpecies();
        when(speciesRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        ReferentialDtos.TemplateResponse response = service.createTemplate(entity.id,
                new ReferentialDtos.TemplateRequest("Standard", Map.of("lactationDays", 305)));

        assertThat(response.speciesId()).isEqualTo(entity.id);
        assertThat(response.name()).isEqualTo("Standard");
        assertThat(response.config()).containsEntry("lactationDays", 305);
        verify(templateRepository).save(any(SpeciesTemplateEntity.class));
    }

    @Test
    void createsBreed() {
        SpeciesEntity entity = existingSpecies();
        when(speciesRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));
        when(breedRepository.existsByTenantIdAndSpeciesIdAndName(tenantId, entity.id, "Holstein"))
                .thenReturn(false);

        ReferentialDtos.BreedResponse response = service.createBreed(entity.id,
                new ReferentialDtos.BreedRequest("Holstein", "holstein"));

        assertThat(response.name()).isEqualTo("Holstein");
        assertThat(response.code()).isEqualTo("holstein");
        verify(breedRepository).save(any(BreedEntity.class));
    }

    @Test
    void rejectsDuplicateBreed() {
        SpeciesEntity entity = existingSpecies();
        when(speciesRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));
        when(breedRepository.existsByTenantIdAndSpeciesIdAndName(tenantId, entity.id, "Holstein"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createBreed(entity.id,
                new ReferentialDtos.BreedRequest("Holstein", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createsVaccinationPlan() {
        SpeciesEntity entity = existingSpecies();
        when(speciesRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        ReferentialDtos.VaccinationPlanResponse response = service.createVaccinationPlan(entity.id,
                new ReferentialDtos.VaccinationPlanRequest("Rage", null, 90, 30,
                        List.of(Map.of("ageDays", 90)), null));

        assertThat(response.name()).isEqualTo("Rage");
        assertThat(response.ageDays()).isEqualTo(90);
        assertThat(response.active()).isTrue();
        verify(vaccinationPlanRepository).save(any(VaccinationPlanEntity.class));
    }

    @Test
    void createsFeedingPlan() {
        SpeciesEntity entity = existingSpecies();
        when(speciesRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        ReferentialDtos.FeedingPlanResponse response = service.createFeedingPlan(entity.id,
                new ReferentialDtos.FeedingPlanRequest("Croissance",
                        List.of(Map.of("stage", "Démarrage"), Map.of("stage", "Finition")), true));

        assertThat(response.name()).isEqualTo("Croissance");
        assertThat(response.stages()).hasSize(2);
        verify(feedingPlanRepository).save(any(FeedingPlanEntity.class));
    }

    @Test
    void createsGrowthCurve() {
        SpeciesEntity entity = existingSpecies();
        when(speciesRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        ReferentialDtos.GrowthCurveResponse response = service.createGrowthCurve(entity.id,
                new ReferentialDtos.GrowthCurveRequest("Standard", null,
                        List.of(Map.of("day", 30, "weightKg", 80))));

        assertThat(response.name()).isEqualTo("Standard");
        assertThat(response.points()).hasSize(1);
        verify(growthCurveRepository).save(any(GrowthCurveEntity.class));
    }

    private SpeciesEntity existingSpecies() {
        SpeciesEntity entity = new SpeciesEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.name = "Bovin";
        entity.code = "bovin";
        entity.productionType = "MILK";
        entity.trackingMode = "INDIVIDUAL";
        return entity;
    }
}
