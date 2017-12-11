package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectprojectdocumentrel : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val projectdocumentrefid = reference("projectdocumentrefid", Projectdocument)
}
class ProjectprojectdocumentrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectprojectdocumentrelEntity>(Projectprojectdocumentrel)

    var projectrefid by Project.id
    var project by ProjectEntity referencedOn Projectprojectdocumentrel.projectrefid
    var created_ts by Projectprojectdocumentrel.created_ts
    var modified_ts by Projectprojectdocumentrel.modified_ts
    var active by Projectprojectdocumentrel.active
    var projectdocumentrefid by Projectprojectdocumentrel.projectdocumentrefid
    var projectdocument by ProjectprojectdocumentrelEntity referencedOn Projectprojectdocumentrel.projectdocumentrefid
}