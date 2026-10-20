package io.railflow.inventory.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchUnitWriteBoundaryTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("io.railflow.inventory.core");

    @Test
    @DisplayName("libs/inventory-core must have zero dependencies on Spring or Jakarta framework classes")
    void noFrameworkDependencies() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("io.railflow.inventory.core..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "javax..");
        rule.check(classes);
    }

    @Test
    @DisplayName("libs/inventory-core must have zero dependencies on external JSON, DB or HTTP packages")
    void noExternalSerializationOrHttp() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("io.railflow.inventory.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.fasterxml.jackson..",
                "org.apache.kafka..",
                "org.hibernate..",
                "java.sql.."
            );
        rule.check(classes);
    }
}
