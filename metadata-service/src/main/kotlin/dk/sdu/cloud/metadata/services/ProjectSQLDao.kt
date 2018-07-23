package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.util.normalize
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
        return Project(id.value.toLong(), fsRoot, owner, description)
    }
}

class ProjectSQLDao : ProjectDAO<Unit> {
    override fun deleteProjectById(session: Unit, id: Long) {
        val deleted = transaction {
            Projects.deleteWhere { Projects.id eq id.toInt() }
        }

        if (deleted != 1) {
            throw ProjectException.NotFound()
        }
    }

    override fun deleteProjectByRoot(session: Unit, root: String) {
        val normalizedPath = root.normalize()
        val deleted = transaction {
            Projects.deleteWhere { Projects.fsRoot eq normalizedPath }
        }

        if (deleted != 1) {
            throw ProjectException.NotFound()
        }
    }

    override fun updateProjectRoot(session: Unit, id: Long, newRoot: String) {
        val convertedId = id.toInt()

        val updated = transaction {
            Projects.update({ Projects.id eq convertedId }, limit = 1) {
                it[fsRoot] = newRoot
            }
        }

        if (updated != 1) {
            throw ProjectException.NotFound()
        }
    }

    override fun findByFSRoot(session: Unit, path: String): Project {
        val normalizedPath = path.normalize()
        return transaction {
            ProjectEntity.find { Projects.fsRoot eq normalizedPath }.toList().singleOrNull()?.toProject()
        } ?: throw ProjectException.NotFound()
    }

    override fun findById(session: Unit, id: Long): Project? {
        val convertedId = id.toInt()
        return transaction { ProjectEntity.findById(convertedId)?.toProject() } ?: throw ProjectException.NotFound()
    }

    override fun createProject(session: Unit, project: Project): Long {
        return transaction {
            ProjectEntity.new {
                fsRoot = project.fsRoot
                description = project.description
                owner = project.owner
            }
        }.id.value.toLong()
    }

    override fun findBestMatchingProjectByPath(session: Unit, path: String): Project {
        val normalizedPath = path.normalize()
        return transaction {
            ProjectEntity.wrapRows(
                Projects
                    .select { stringLiteral(normalizedPath) like Concat(Projects.fsRoot, stringLiteral("%")) }
                    .orderBy(CharLength(Projects.fsRoot), isAsc = false)
                    .limit(1)
            ).toList().singleOrNull()?.toProject()
        } ?: throw ProjectException.NotFound()
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
    LikeOp(this, expr)
