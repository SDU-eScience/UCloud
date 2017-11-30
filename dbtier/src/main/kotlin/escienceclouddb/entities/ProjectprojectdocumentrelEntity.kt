package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectprojectdocumentrel : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val projectdocumentrefid = reference("projectdocumentrefid", Projectdocument)
}
class ProjectprojectdocumentrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectprojectdocumentrelEntity>(Projectprojectdocumentrel)

    var projectrefid by Project.id
    var project by ProjectEntity referencedOn Projectprojectdocumentrel.projectrefid
    var lastmodified by Projectprojectdocumentrel.lastmodified
    var active by Projectprojectdocumentrel.active
    var projectdocumentrefid by Projectprojectdocumentrel.projectdocumentrefid
    var projectdocument by ProjectprojectdocumentrelEntity referencedOn Projectprojectdocumentrel.projectdocumentrefid
}