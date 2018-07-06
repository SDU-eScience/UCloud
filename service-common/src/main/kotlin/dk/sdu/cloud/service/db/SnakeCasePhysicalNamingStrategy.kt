package dk.sdu.cloud.service.db

import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

class SnakeCasePhysicalNamingStrategy : PhysicalNamingStrategy {
    override fun toPhysicalCatalogName(identifier: Identifier?, jdbcEnv: JdbcEnvironment?): Identifier? {
        return identifier.convertToSnakeCase()
    }

    override fun toPhysicalColumnName(identifier: Identifier?, jdbcEnv: JdbcEnvironment?): Identifier? {
        return identifier.convertToSnakeCase()
    }

    override fun toPhysicalSchemaName(identifier: Identifier?, jdbcEnv: JdbcEnvironment?): Identifier? {
        return identifier.convertToSnakeCase()
    }

    override fun toPhysicalSequenceName(identifier: Identifier?, jdbcEnv: JdbcEnvironment?): Identifier? {
        return identifier.convertToSnakeCase()
    }

    override fun toPhysicalTableName(identifier: Identifier?, jdbcEnv: JdbcEnvironment?): Identifier? {
        return identifier.convertToSnakeCase()
    }

    private fun Identifier?.convertToSnakeCase(): Identifier? {
        if (this == null) return null
        if (text.isNullOrBlank()) return this
        return Identifier.toIdentifier(text.replace(regex, "$1_$2").toLowerCase())
    }

    companion object {
        private val regex = Regex("([a-z])([A-Z])")
    }
}