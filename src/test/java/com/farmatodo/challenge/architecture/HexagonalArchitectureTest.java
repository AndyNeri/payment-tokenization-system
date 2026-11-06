package com.farmatodo.challenge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Hexagonal rules inside every slice (ADR-001):
 * the domain is plain Java, and inner layers never depend on outer ones.
 */
@AnalyzeClasses(packages = "com.farmatodo.challenge", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "tools.jackson..")
            .allowEmptyShould(true)
            .because("the domain must be testable and portable without frameworks");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .allowEmptyShould(true)
            .because("dependencies point inwards: infrastructure -> application -> domain");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)
            .because("use cases talk to the outside world only through ports");

    @ArchTest
    static final ArchRule controllersLiveInInboundAdapters = noClasses()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideOutsideOfPackages("..infrastructure.in.web..", "..shared.web..")
            .allowEmptyShould(true)
            .because("REST controllers are inbound adapters");
}
