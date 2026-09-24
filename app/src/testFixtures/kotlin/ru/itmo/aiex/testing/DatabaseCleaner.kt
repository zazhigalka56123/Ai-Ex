package ru.itmo.aiex.testing

import org.springframework.jdbc.core.JdbcTemplate

class DatabaseCleaner(private val jdbc: JdbcTemplate) {
    fun clean() {
        val tables =
            jdbc
                .queryForList(
                    """
                    SELECT table_schema || '.' || table_name
                    FROM information_schema.tables
                    WHERE table_type = 'BASE TABLE' AND table_schema = ANY (?)
                    """.trimIndent(),
                    String::class.java,
                    MODULE_SCHEMAS,
                ).filterNot { it in REFERENCE_TABLES }
        if (tables.isNotEmpty()) {
            jdbc.execute("TRUNCATE TABLE ${tables.joinToString()} CASCADE")
        }
    }

    companion object {
        private val MODULE_SCHEMAS = arrayOf("iam", "persona", "ingest", "dialog", "agent", "care", "admin", "notification")
        val REFERENCE_TABLES = setOf("iam.roles", "persona.tags", "care.specializations")
    }
}
