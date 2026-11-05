package ru.itmo.aiex.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated
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
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchitectureTest {
    private lateinit var classes: JavaClasses

    @BeforeAll
    fun importClasses() {
        classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("ru.itmo.aiex")
    }

    @Test
    fun `сущности живут в entity, контроллеры в controller, Spring Data - в repository`() {
        classes().that().areAnnotatedWith(Entity::class.java).should().resideInAPackage("..entity..").check(classes)
        classes().that().areAnnotatedWith(RestController::class.java).should().resideInAPackage("..controller..").check(classes)
        classes()
            .that()
            .areInterfaces()
            .and()
            .areAssignableTo(Repository::class.java)
            .should()
            .resideInAPackage("..repository..")
            .check(classes)
    }

    @Test
    fun `сервисы живут в service`() {
        classes()
            .that()
            .areAnnotatedWith(Service::class.java)
            .should()
            .resideInAPackage("..service..")
            .check(classes)
    }

    @Test
    fun `контроллер ходит в БД только через сервис`() {
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("ru.itmo.aiex.*.repository..")
            .check(classes)
    }

    @Test
    fun `нижние слои не знают про контроллеры`() {
        noClasses()
            .that()
            .resideInAnyPackage("..service..", "..repository..", "..entity..", "..dto..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("ru.itmo.aiex.*.controller..")
            .check(classes)
    }

    @Test
    fun `сущности и репозитории не зависят от DTO`() {
        noClasses()
            .that()
            .resideInAnyPackage("..entity..", "..repository..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("ru.itmo.aiex.*.dto..")
            .check(classes)
    }

    @Test
    fun `Entity не утекает в DTO`() {
        noFields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage("..dto..", "..controller..")
            .should()
            .haveRawType(CanBeAnnotated.Predicates.annotatedWith(Entity::class.java))
            .check(classes)
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
}
