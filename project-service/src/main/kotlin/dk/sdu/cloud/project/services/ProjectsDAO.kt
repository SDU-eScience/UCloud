package dk.sdu.cloud.project.services

import dk.sdu.cloud.project.api.Project
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

interface CommonAttributes {
    var modifiedAt: DateTime
    var createdAt: DateTime
    var markedForDelete: Boolean
    var active: Boolean
}

class CommonColumns(
        // TODO Are the timestamps bound to event time or event processing?
        val modifiedAt: Column<DateTime>,
        val createdAt: Column<DateTime>,
        val markedForDelete: Column<Boolean>,
        val active: Column<Boolean>
)

fun IdTable<*>.commonAttributes(): CommonColumns =
        CommonColumns(
                modifiedAt = datetime("modified_ts"),
                createdAt = datetime("created_ts"),
                markedForDelete = bool("markedfordelete"),
                active = bool("active")
        )

fun <ID : Any> Entity<ID>.commonAttributes(columns: CommonColumns): CommonAttributes =
        object : CommonAttributes {
            // TODO Not sure how to bind to the delegate. This should still work though.
            private val entity = this@commonAttributes

            override var modifiedAt: DateTime
                get() = columns.modifiedAt.getValue(entity, this::modifiedAt)
                set(value) {
                    columns.modifiedAt.setValue(entity, this::modifiedAt, value)
                }

            override var createdAt: DateTime
                get() = columns.createdAt.getValue(entity, this::createdAt)
                set(value) {
                    columns.createdAt.setValue(entity, this::createdAt, value)
                }

            override var markedForDelete: Boolean
                get() = columns.markedForDelete.getValue(entity, this::markedForDelete)
                set(value) {
                    columns.markedForDelete.setValue(entity, this::markedForDelete, value)
                }

            override var active: Boolean
                get() = columns.active.getValue(entity, this::active)
                set(value) {
                    columns.active.setValue(entity, this::active, value)
                }
        }

class ProjectsDAO {
    fun findAllMyProjects(who: String): List<Project> = TODO()

    fun findById(id: Long): Project? =
            transaction { ProjectRow.findById(id)?.toProject() }
}

object ProjectsTable : LongIdTable("project") {
    val name = varchar("projectname", 256)
    val startAt = datetime("projectstart")
    val endAt = datetime("projectend")
    val shortName = varchar("projectshortname", 256)
    val common = commonAttributes()

    // TODO I would like to remove these
    val irodsGroupAdmin = varchar("irodsgroupadmin", 256)
    val irodsGroupId = long("irodsgroupidmap")
}

// TODO This might just be a bit redundant since we need create the type for communication
class ProjectRow(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ProjectRow>(ProjectsTable)

    var name by ProjectsTable.name
    var startAt by ProjectsTable.startAt
    var endAt by ProjectsTable.endAt
    var shortName by ProjectsTable.shortName
    var irodsGroupAdmin by ProjectsTable.irodsGroupAdmin
    var irodsGroupId by ProjectsTable.irodsGroupId
    val common = commonAttributes(ProjectsTable.common)

    // TODO This is not ideal, but we cannot export the database rows to other clients
    // TODO Should we export the common attributes too?
    fun toProject(): Project = Project(
            id = id.value,
            name = name,
            startAt = startAt.millis,
            endAt = endAt.millis,
            shortName = shortName
    )
}

