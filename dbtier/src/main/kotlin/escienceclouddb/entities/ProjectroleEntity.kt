package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectrole : IntIdTable() {
    val irodsrolemap = text("irodsrolemap").nullable()
    val projectroletext = text("projectroletext").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
}
class ProjectroleEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectroleEntity>(Projectrole)

    var irodsrolemap by Projectrole.irodsrolemap
    var projectroletext by Projectrole.projectroletext
    var lastmodified by Projectrole.lastmodified
    var active by Projectrole.active
}