package com.choco.reporting.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the Clean Architecture dependency rule for the reporting service.
 *
 * <p>Dependencies point inward: adapters may depend on the application and the domain, never the
 * reverse. The rule matters here because the reporting service is almost entirely derived data — the
 * projection rules, the window arithmetic and the export minimisation are the parts worth testing, and
 * they are only testable as plain objects if the domain and the use cases carry no Spring, no JPA and
 * no spreadsheet library. A report that could only be checked by booting the service is a report whose
 * revenue arithmetic nobody checks.
 *
 * <p>The reporting service is also a pure consumer: it publishes no events, so the application layer
 * must not depend on the messaging or outbox machinery either.
 */
@DisplayName("Clean Architecture boundaries")
class CleanArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.choco.reporting");
    }

    @Test
    @DisplayName("the domain layer depends on no framework and no adapter")
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..reporting.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "org.springframework.amqp..",
                        "org.apache.poi..",
                        "com.choco.reporting.adapter..",
                        "com.choco.reporting.application..");

        rule.check(classes);
    }

    @Test
    @DisplayName("the application layer depends on no adapter and no persistence or export framework")
    void applicationIsAdapterFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..reporting.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.choco.reporting.adapter..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.apache.poi..",
                        "org.springframework.amqp..");

        rule.check(classes);
    }

    @Test
    @DisplayName("inbound adapters do not reach into the persistence layer")
    void inboundAdaptersDoNotTouchPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..reporting.adapter.in..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..reporting.adapter.out.persistence..");

        rule.check(classes);
    }

    @Test
    @DisplayName("the spreadsheet library never leaves the export adapter")
    void spreadsheetLibraryStaysInTheExportAdapter() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..reporting.adapter.out.export..")
                .should().dependOnClassesThat().resideInAnyPackage("org.apache.poi..");

        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on the application's internal wiring configuration")
    void nothingDependsOnWiringConfiguration() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..reporting.adapter.in.config..")
                .should().dependOnClassesThat().resideInAPackage("..reporting.adapter.in.config..");

        rule.check(classes);
    }
}
