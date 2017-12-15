package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projecttype : IntIdTable() {
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val projecttypename = text("projecttypename").nullable()
    val active = integer("active").nullable()
}
class ProjecttypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjecttypeEntity>(Logintype)

    var created_ts by Projecttype.created_ts
    var modified_ts by Projecttype.modified_ts
    var projecttypename by Projecttype.projecttypename
    var active by Projecttype.active
}