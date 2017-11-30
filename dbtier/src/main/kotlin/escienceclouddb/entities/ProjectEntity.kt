package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Project : IntIdTable() {
    val projectname = text("projectname").nullable()
    val projectstart = datetime("projectstart").nullable()
    val projectshortname = text("projectshortname").nullable()
    val projectend = datetime("projectend").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val irodsgroupadmin = text("irodsgroupadmin").nullable()
    val irodsgroupidmap = integer("irodsgroupidmap").nullable()
}
class ProjectEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectEntity>(Project)

    var projectname by Project.projectname
    var projectstart by Project.projectstart
    var projectshortname by Project.projectshortname
    var projectend by Project.projectend
    var lastmodified by Project.lastmodified
    var active by Project.active
    var irodsgroupadmin by Project.irodsgroupadmin
    var irodsgroupidmap by Project.irodsgroupidmap
}