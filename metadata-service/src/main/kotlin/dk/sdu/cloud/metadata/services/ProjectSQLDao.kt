package dk.sdu.cloud.metadata.services

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.transactions.transaction

object Projects : IntIdTable() {
    val fsRoot = text("fs_root").uniqueIndex()
    val owner = text("owner")
    val description = text("description")
}

class ProjectEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ProjectEntity>(Projects)

    var fsRoot by Projects.fsRoot
    var owner by Projects.owner
    var description by Projects.description

    fun toProject(): Project {
        return Project(id.value.toString(), fsRoot, owner, description)
    }
}

class ProjectSQLDao : ProjectDAO {
    override fun findByFSRoot(path: String): Project? {
        return transaction {
            ProjectEntity.find { Projects.fsRoot eq path }.toList().singleOrNull()?.toProject()
        }
    }

    override fun findById(id: String): Project? {
        val convertedId = id.toIntOrNull() ?: return null
        return transaction { ProjectEntity.findById(convertedId)?.toProject() }
    }

    override fun createProject(project: Project): String {
        return transaction {
            ProjectEntity.new {
                fsRoot = project.fsRoot
                description = project.description
                owner = project.owner
            }
        }.id.value.toString()
    }

    override fun findBestMatchingProjectByPath(path: String): Project? {
        return transaction {
            ProjectEntity.wrapRows(
                Projects
                    .select { stringLiteral(path) like Concat(Projects.fsRoot, stringLiteral("%")) }
                    .orderBy(CharLength(Projects.fsRoot), isAsc = false)
                    .limit(1)
            ).toList().singleOrNull()?.toProject()
        }
    }
}

class CharLength<T : String?>(private val expr: Expression<T>) : Function<T>(VarCharColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "CHAR_LENGTH(${expr.toSQL(queryBuilder)})"
}

class Concat<T : String?>(private val expr: Expression<T>, private val other: Expression<T>) :
    Function<T>(VarCharColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String =
        "CONCAT(${expr.toSQL(queryBuilder)}, ${other.toSQL(queryBuilder)})"
}

infix fun <T : String?> ExpressionWithColumnType<T>.like(expr: Expression<T>): Op<Boolean> =
    LikeOp(this, QueryParameter(expr, columnType))
