package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Projectresearchtype : IntIdTable() {
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val projectresearchtypetext = text("projectresearchtypetext").nullable()
    val active = integer("active").nullable()
}
class ProjectresearchtypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectresearchtypeEntity>(Projectresearchtype)

    var created_ts by Projectresearchtype.created_ts
    var modified_ts by Projectresearchtype.modified_ts
    var projectresearchtypetext by Projectresearchtype.projectresearchtypetext
    var active by Projectresearchtype.active
}