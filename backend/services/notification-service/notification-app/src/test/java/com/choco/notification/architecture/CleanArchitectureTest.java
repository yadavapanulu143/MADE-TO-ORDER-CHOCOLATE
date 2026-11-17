package com.choco.notification.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the Clean Architecture dependency rule for the notification service.
 *
 * <p>Dependencies point inward: adapters may depend on the application and the domain, never the
 * reverse. The line matters here for the same reason it matters everywhere else — the rule that a
 * message is sent once per event, that a failure is recorded rather than thrown, and that a resend
 * only revives a failed message must all be testable with no database, no broker and no mail server in
 * the room. Those are the tests that run in a fraction of a second, and they only exist if the domain
 * and the use case can be constructed as plain objects.
 */
@DisplayName("Clean Architecture boundaries")
class CleanArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.choco.notification");
    }

    @Test
    @DisplayName("the domain layer depends on no framework and no adapter")
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..notification.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.mail..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "org.springframework.amqp..",
                        "com.choco.notification.adapter..",
                        "com.choco.notification.application..");

        rule.check(classes);
    }

    @Test
    @DisplayName("the application layer depends on no adapter and no persistence or mail framework")
    void applicationIsAdapterFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..notification.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.choco.notification.adapter..",
                        "jakarta.persistence..",
                        "jakarta.mail..",
                        "org.hibernate..",
                        "org.springframework.amqp..",
                        "org.springframework.mail..");

        rule.check(classes);
    }

    @Test
    @DisplayName("inbound adapters do not reach into the persistence layer")
    void inboundAdaptersDoNotTouchPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..notification.adapter.in..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..notification.adapter.out.persistence..");

        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on the application's internal wiring configuration")
    void nothingDependsOnWiringConfiguration() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..notification.adapter.in.config..")
                .should().dependOnClassesThat().resideInAPackage("..notification.adapter.in.config..");

        rule.check(classes);
    }
}
