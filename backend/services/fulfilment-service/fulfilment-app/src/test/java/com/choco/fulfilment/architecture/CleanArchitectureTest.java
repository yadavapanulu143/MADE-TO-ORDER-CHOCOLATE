package com.choco.fulfilment.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the Clean Architecture dependency rule for the fulfilment service.
 *
 * <p>Dependencies point inward: infrastructure and interfaces may depend on the domain and the
 * application layer, never the reverse. The line matters most here for the made-to-order rules: the
 * decisions that say a job cannot be marked ready before it has been prepared, or that a delivery
 * cannot go out before it is assigned, must be testable with no database and no broker in the room.
 *
 * <p>These assertions are about packages, so a new class is covered the moment it is added.
 */
@DisplayName("Clean Architecture boundaries")
class CleanArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.choco.fulfilment");
    }

    @Test
    @DisplayName("the domain layer depends on no framework and no adapter")
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..fulfilment.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "org.springframework.amqp..",
                        "com.choco.fulfilment.adapter..",
                        "com.choco.fulfilment.application..");

        rule.check(classes);
    }

    @Test
    @DisplayName("the application layer depends on no adapter and no persistence framework")
    void applicationIsAdapterFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..fulfilment.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.choco.fulfilment.adapter..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework.amqp..");

        rule.check(classes);
    }

    @Test
    @DisplayName("inbound adapters do not reach into the persistence layer")
    void inboundAdaptersDoNotTouchPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..fulfilment.adapter.in..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..fulfilment.adapter.out.persistence..");

        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on the application's internal wiring configuration")
    void nothingDependsOnWiringConfiguration() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..fulfilment.adapter.in.config..")
                .should().dependOnClassesThat().resideInAPackage("..fulfilment.adapter.in.config..");

        rule.check(classes);
    }
}
