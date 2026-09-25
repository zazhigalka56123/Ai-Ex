package ru.itmo.aiex.common.persistence

import jakarta.persistence.Column
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.lang.reflect.Field

class EnumStorageIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `все enum-поля сущностей хранятся строками`() {
        val enumFields =
            entityManagerFactory.metamodel.entities.flatMap { entity ->
                val javaType = entity.javaType
                generateSequence<Class<*>>(javaType) { it.superclass }
                    .flatMap { it.declaredFields.asSequence() }
                    .filter { it.type.isEnum }
                    .map { javaType to it }
                    .toList()
            }
        assertThat(enumFields).describedAs("в модели есть enum-поля").isNotEmpty()

        enumFields.forEach { (entity, field) ->
            val enumerated = field.getAnnotation(Enumerated::class.java)
            assertThat(enumerated?.value).describedAs("${entity.simpleName}.${field.name}").isEqualTo(EnumType.STRING)

            val table = entity.getAnnotation(Table::class.java)
            val dataType =
                jdbcTemplate.queryForObject(
                    "SELECT data_type FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                    String::class.java,
                    table.schema,
                    table.name,
                    columnName(field),
                )
            assertThat(dataType).describedAs("колонка ${table.schema}.${table.name}.${columnName(field)}").isEqualTo("character varying")
        }
    }

    private fun columnName(field: Field): String = field.getAnnotation(Column::class.java)?.name?.takeIf { it.isNotBlank() }
        ?: field.name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}
