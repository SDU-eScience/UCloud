package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectprojectresearchtyperel : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val projectresearchtyperefid = reference("projectresearchtyperefid", Projectresearchtype)
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
}
class ProjectprojectresearchtyperelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectprojectresearchtyperelEntity>(Projectprojectresearchtyperel)

    var projectrefid by Projectprojectresearchtyperel.projectrefid
    var project by ProjectEntity referencedOn Projectprojectresearchtyperel.projectrefid
    var projectresearchtyperefid by Projectprojectresearchtyperel.projectresearchtyperefid
    var projectresearchtype by ProjectresearchtypeEntity referencedOn Projectprojectresearchtyperel.projectresearchtyperefid
    var created_ts by Projectprojectresearchtyperel.created_ts
    var modified_ts by Projectprojectresearchtyperel.modified_ts
    var active by Projectprojectresearchtyperel.active
}