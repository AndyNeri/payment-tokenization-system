package com.farmatodo.challenge.architecture;

import com.farmatodo.challenge.PaymentTokenizationSystemApplication;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the vertical slices are real modules: no dependency cycles and no slice reaching into another slice's
 * internal packages. Also generates module diagrams (PlantUML) under target/spring-modulith-docs.
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(PaymentTokenizationSystemApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
