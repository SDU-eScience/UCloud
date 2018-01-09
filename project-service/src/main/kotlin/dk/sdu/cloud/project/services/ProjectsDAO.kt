package dk.sdu.cloud.project.services

import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.project.util.EnumTable
import dk.sdu.cloud.project.util.commonAttributes
import dk.sdu.cloud.project.util.enumCache
import dk.sdu.cloud.project.util.mapSingle
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class ProjectsDAO {
    private fun mapProjectRow(it: ResultRow): Project = with(ProjectsTable) {
        Project(it[id], it[name], it[startAt].millis, it[endAt].millis, it[shortName])
    }

    fun findAllMyProjects(who: String): List<Project> = TODO()

    fun findById(id: Long): Project? =
            transaction { ProjectsTable.select { ProjectsTable.id eq id }.mapSingle(::mapProjectRow) }

    fun findByIdWithMembers(id: Long): ProjectWithMembers? {
        val rows = transaction {
            (ProjectsTable leftJoin ProjectPersonRelTable)
                    .select { ProjectsTable.id eq id }
                    .toList()
        }

        if (rows.isEmpty()) return null
        val project = mapProjectRow(rows[0])
        val members = if (rows[0].hasValue(ProjectPersonRelTable.person)) {
            // Project exists with members
            rows.map {
                ProjectMember(
                        it[ProjectPersonRelTable.person],
                        ProjectRoleTable.resolveById(it[ProjectPersonRelTable.role])
                )
            }
        } else {
            // Project exists but has no members
            emptyList()
        }

        return ProjectWithMembers(project, members)
    }
}

object ProjectsTable : Table("project") {
    val id = long("id").primaryKey()
    val name = varchar("projectname", 256)
    val startAt = datetime("projectstart")
    val endAt = datetime("projectend")
    val shortName = varchar("projectshortname", 256)
    val common = commonAttributes()

    // TODO I would like to remove these
    val irodsGroupAdmin = varchar("irodsgroupadmin", 256)
    val irodsGroupId = long("irodsgroupidmap")
}

object ProjectPersonRelTable : Table("projectpersonrel") {
    val id = long("id").primaryKey()
    val project = long("projectrefid") references ProjectsTable.id

    // TODO Foreign keys to table owned by different service
    // Having a foreign key to person seems odd and bad for performance (unless we want to violate access
    // to private tables)
    val person = long("personrefid")

    val role = long("projectrolerefid")
    val common = commonAttributes()
}

object ProjectTypeTable : Table("projecttype"), EnumTable<Long, ProjectType> {
    val id = long("id").primaryKey()
    val name = varchar("projecttypeename", 128)
    val common = commonAttributes()

    override val enumResolver = enumCache(id, name, ProjectType.UNKNOWN) { it.typeName }
}

object ProjectRoleTable : Table("projectrole"), EnumTable<Long, ProjectRole> {
    val id = long("id").primaryKey()
    val name = varchar("projectrolename", 128)
    val common = commonAttributes()

    override val enumResolver = enumCache(id, name, ProjectRole.UNKNOWN) { it.roleName }
}