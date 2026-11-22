package com.rmbreeding.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {

    @Test
    void modulesAreWellDefined() {
        ApplicationModules modules = ApplicationModules.of(RmBreedingApplication.class);
        modules.verify();
    }
}
