package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Projectresearchtype : IntIdTable() {
    val lastmodified = datetime("lastmodified")
    val projectresearchtypetext = text("projectresearchtypetext").nullable()
    val active = integer("active").nullable()
}
class ProjectresearchtypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectresearchtypeEntity>(Projectresearchtype)

    var lastmodified by Projectresearchtype.lastmodified
    var projectresearchtypetext by Projectresearchtype.projectresearchtypetext
    var active by Projectresearchtype.active
}