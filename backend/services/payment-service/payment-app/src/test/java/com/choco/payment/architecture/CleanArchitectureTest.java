package com.choco.payment.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the Clean Architecture dependency rule for the payment service.
 *
 * <p>Dependencies point inward: infrastructure and interfaces may depend on the domain and the
 * application layer, never the reverse. The payment service is the one with the strongest reason to
 * hold this line: the Razorpay SDK must never reach the domain, or the business rules that decide
 * whether money was received would no longer be testable without the provider.
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
                .importPackages("com.choco.payment");
    }

    @Test
    @DisplayName("the domain layer depends on no framework and no provider SDK")
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..payment.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "com.razorpay..",
                        "org.json..",
                        "com.choco.payment.adapter..",
                        "com.choco.payment.application..");

        rule.check(classes);
    }

    @Test
    @DisplayName("the application layer depends on no adapter, persistence framework or provider SDK")
    void applicationIsAdapterFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..payment.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.choco.payment.adapter..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.razorpay..",
                        "org.json..");

        rule.check(classes);
    }

    @Test
    @DisplayName("only the outbound gateway adapter may reference the provider SDK")
    void providerSdkIsConfinedToTheGatewayAdapter() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..payment.adapter.out.gateway..")
                .should().dependOnClassesThat().resideInAnyPackage("com.razorpay..", "org.json..");

        rule.check(classes);
    }

    @Test
    @DisplayName("inbound adapters do not reach into the persistence layer")
    void inboundAdaptersDoNotTouchPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..payment.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage("..payment.adapter.out.persistence..");

        rule.check(classes);
    }

    @Test
    @DisplayName("no class depends on the application's internal wiring configuration")
    void nothingDependsOnWiringConfiguration() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..payment.adapter.in.config..")
                .should().dependOnClassesThat().resideInAPackage("..payment.adapter.in.config..");

        rule.check(classes);
    }
}
