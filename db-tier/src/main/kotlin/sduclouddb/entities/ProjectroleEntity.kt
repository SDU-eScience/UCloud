package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectrole : IntIdTable() {
    val irodsrolemap = text("irodsrolemap").nullable()
    val projectrolename = text("projectrolename").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
}
class ProjectroleEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectroleEntity>(Projectrole)

    var irodsrolemap by Projectrole.irodsrolemap
    var projectrolename by Projectrole.projectrolename
    var created_ts by Projectrole.created_ts
    var modified_ts by Projectrole.modified_ts
    var active by Projectrole.active
}