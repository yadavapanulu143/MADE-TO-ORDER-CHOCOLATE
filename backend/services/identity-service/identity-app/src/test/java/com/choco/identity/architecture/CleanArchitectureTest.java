package com.choco.identity.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the Clean Architecture dependency rule for the identity service.
 *
 * <p>The rule is that dependencies point inward: infrastructure and interfaces may depend on the
 * domain and application layers, never the reverse. Without a test this erodes silently, because a
 * single convenient import in a domain class compiles fine and is only noticed much later, when the
 * domain can no longer be tested or reused without a framework.
 *
 * <p>These assertions are about packages, not individual classes, so a new class is covered the
 * moment it is added.
 */
@DisplayName("Clean Architecture boundaries")
class CleanArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.choco.identity");
    }

    @Test
    @DisplayName("the domain layer depends on no framework")
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..identity.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "com.choco.identity.adapter..",
                        "com.choco.identity.application..");

        rule.check(classes);
    }

    @Test
    @DisplayName("the application layer depends on no adapter or persistence framework")
    void applicationIsAdapterFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..identity.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.choco.identity.adapter..",
                        "jakarta.persistence..",
                        "org.hibernate..");

        rule.check(classes);
    }

    @Test
    @DisplayName("inbound adapters do not reach into the persistence layer")
    void inboundAdaptersDoNotTouchPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..identity.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage("..identity.adapter.out.persistence..");

        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on the application's internal wiring configuration")
    void nothingDependsOnWiringConfiguration() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..identity.adapter.in.config..")
                .should().dependOnClassesThat().resideInAPackage("..identity.adapter.in.config..");

        rule.check(classes);
    }
}
