package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectprojectresearchtyperel : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val projectresearchtyperefid = reference("projectresearchtyperefid", Projectresearchtype)
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
}
class ProjectprojectresearchtyperelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectprojectresearchtyperelEntity>(Projectprojectresearchtyperel)

    var projectrefid by Projectprojectresearchtyperel.projectrefid
    var project by ProjectEntity referencedOn Projectprojectresearchtyperel.projectrefid
    var projectresearchtyperefid by Projectprojectresearchtyperel.projectresearchtyperefid
    var projectresearchtype by ProjectresearchtypeEntity referencedOn Projectprojectresearchtyperel.projectresearchtyperefid
    var lastmodified by Projectprojectresearchtyperel.lastmodified
    var active by Projectprojectresearchtyperel.active
}