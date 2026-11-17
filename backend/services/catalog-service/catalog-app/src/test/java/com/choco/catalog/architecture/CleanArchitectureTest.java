package com.choco.catalog.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the Clean Architecture dependency rule for the catalog service.
 *
 * <p>Dependencies point inward: infrastructure and interfaces may depend on the domain and the
 * application layer, never the reverse. Without a test the rule erodes silently, because one
 * convenient import of a JPA annotation or a Spring stereotype in a domain class compiles fine and is
 * only noticed much later, when the domain can no longer be exercised without a container.
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
                .importPackages("com.choco.catalog");
    }

    @Test
    @DisplayName("the domain layer depends on no framework")
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..catalog.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "com.choco.catalog.adapter..",
                        "com.choco.catalog.application..");

        rule.check(classes);
    }

    @Test
    @DisplayName("the application layer depends on no adapter or persistence framework")
    void applicationIsAdapterFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..catalog.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.choco.catalog.adapter..",
                        "jakarta.persistence..",
                        "org.hibernate..");

        rule.check(classes);
    }

    @Test
    @DisplayName("inbound adapters do not reach into the persistence layer")
    void inboundAdaptersDoNotTouchPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..catalog.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage("..catalog.adapter.out.persistence..");

        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on the application's internal wiring configuration")
    void nothingDependsOnWiringConfiguration() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..catalog.adapter.in.config..")
                .should().dependOnClassesThat().resideInAPackage("..catalog.adapter.in.config..");

        rule.check(classes);
    }
}
