package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectorgrel : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val orgrefid = reference("orgrefid", Org)
}
class ProjectorgrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectorgrelEntity>(Projectorgrel)

    var projectrefid by Projectorgrel.projectrefid
    var project by ProjectEntity referencedOn Projectorgrel.projectrefid
    var lastmodified by Projectorgrel.lastmodified
    var active by Projectorgrel.active
    var orgrefid by Org.id
    var org by OrgEntity referencedOn Projectorgrel.orgrefid

}