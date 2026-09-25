package ru.itmo.aiex.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import jakarta.persistence.Entity
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.repository.Repository
import org.springframework.web.bind.annotation.RestController

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchitectureTest {
    private lateinit var classes: JavaClasses

    private val modules = listOf("iam", "persona", "ingest", "agent", "dialog", "care", "admin", "notification", "llm")

    @BeforeAll
    fun importClasses() {
        classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("ru.itmo.aiex")
    }

    @Test
    fun `домен не знает про Spring, веб и инфраструктуру`() {
        classes()
            .that()
            .resideInAPackage("ru.itmo.aiex.*.domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "java..",
                "kotlin..",
                "org.jetbrains.annotations..",
                "jakarta.persistence..",
                "jakarta.validation..",
                "org.hibernate.annotations..",
                "org.hibernate.type..",
                "ru.itmo.aiex.common..",
                "ru.itmo.aiex.*.domain..",
                "ru.itmo.aiex.*.api..",
            ).allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `application не зависит от web и infrastructure - только от портов домена`() {
        noClasses()
            .that()
            .resideInAPackage("ru.itmo.aiex.*.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("ru.itmo.aiex.*.web..", "ru.itmo.aiex.*.infrastructure..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `контракты -api не тянут Spring и JPA`() {
        noClasses()
            .that()
            .resideInAPackage("ru.itmo.aiex.*.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
            .check(classes)
    }

    @Test
    fun `чужие внутренности модуля недоступны - только его -api`() {
        modules.forEach { module ->
            noClasses()
                .that()
                .resideOutsideOfPackage("ru.itmo.aiex.$module..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "ru.itmo.aiex.$module.domain..",
                    "ru.itmo.aiex.$module.application..",
                    "ru.itmo.aiex.$module.infrastructure..",
                    "ru.itmo.aiex.$module.web..",
                ).allowEmptyShould(true)
                .because("межмодульный вызов - только через $module-api")
                .check(classes)
        }
    }

    @Test
    fun `с провайдером LLM общается только agent`() {
        noClasses()
            .that()
            .resideOutsideOfPackages("ru.itmo.aiex.agent..", "ru.itmo.aiex.llm..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("ru.itmo.aiex.llm..")
            .because("каждый вызов провайдера обязан оставить строку в agent_runs")
            .check(classes)
    }

    @Test
    fun `сущности живут в domain, контроллеры в web, Spring Data - в infrastructure`() {
        classes().that().areAnnotatedWith(Entity::class.java).should().resideInAPackage("..domain..").check(classes)
        classes().that().areAnnotatedWith(RestController::class.java).should().resideInAPackage("..web..").check(classes)
        classes()
            .that()
            .areInterfaces()
            .and()
            .areAssignableTo(Repository::class.java)
            .should()
            .resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `Entity не утекает в веб-DTO`() {
        noFields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("..web..")
            .should()
            .haveRawType(com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith(Entity::class.java))
            .allowEmptyShould(true)
            .check(classes)
    }
}
