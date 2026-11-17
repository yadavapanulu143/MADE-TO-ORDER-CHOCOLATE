package com.choco.order.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the Clean Architecture dependency rule for the order service.
 *
 * <p>Dependencies point inward: infrastructure and interfaces may depend on the domain and the
 * application layer, never the reverse. The order service has a particular reason to hold this line:
 * its domain encodes the made-to-order rule — an order is never prepared before a verified payment —
 * and that rule must stay exercisable in a plain unit test, without a database, a broker or another
 * service on the classpath.
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
                .importPackages("com.choco.order");
    }

    @Test
    @DisplayName("the domain layer depends on no framework and no adapter")
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "com.choco.order.adapter..",
                        "com.choco.order.application..");

        rule.check(classes);
    }

    @Test
    @DisplayName("the application layer depends on no adapter or persistence framework")
    void applicationIsAdapterFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.choco.order.adapter..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework.web..");

        rule.check(classes);
    }

    @Test
    @DisplayName("inbound adapters do not reach into the persistence layer")
    void inboundAdaptersDoNotTouchPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage("..order.adapter.out.persistence..");

        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on the application's internal wiring configuration")
    void nothingDependsOnWiringConfiguration() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..order.adapter.in.config..")
                .should().dependOnClassesThat().resideInAPackage("..order.adapter.in.config..");

        rule.check(classes);
    }
}
