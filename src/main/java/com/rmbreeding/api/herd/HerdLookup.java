package com.rmbreeding.api.herd;

import com.rmbreeding.api.shared.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HerdLookup {

    private final AnimalRepository animalRepository;

    public HerdLookup(AnimalRepository animalRepository) {
        this.animalRepository = animalRepository;
    }

    public AnimalInfo requireAnimal(UUID tenantId, UUID animalId) {
        AnimalEntity animal = animalRepository.findByIdAndTenantId(animalId, tenantId)
                .orElseThrow(() -> new NotFoundException("Animal not found"));
        return new AnimalInfo(animal.id, animal.farmId, animal.speciesId, animal.sex, animal.status);
    }

    public record AnimalInfo(UUID id, UUID farmId, UUID speciesId, String sex, String status) {
    }
}
