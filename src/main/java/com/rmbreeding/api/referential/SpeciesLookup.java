package com.rmbreeding.api.referential;

import com.rmbreeding.api.shared.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class SpeciesLookup {

    private final SpeciesRepository speciesRepository;

    public SpeciesLookup(SpeciesRepository speciesRepository) {
        this.speciesRepository = speciesRepository;
    }

    public SpeciesInfo requireSpecies(UUID tenantId, UUID speciesId) {
        SpeciesEntity species = speciesRepository.findByIdAndTenantId(speciesId, tenantId)
                .orElseThrow(() -> new NotFoundException("Species not found"));
        return new SpeciesInfo(species.id, species.gestationDays, species.heatCycleDays,
                species.weaningAgeDays, species.settings);
    }

    public record SpeciesInfo(UUID id, Integer gestationDays, Integer heatCycleDays,
            Integer weaningAgeDays, Map<String, Object> settings) {
    }
}
