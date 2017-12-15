package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Project : IntIdTable() {
    val projectname = text("projectname").nullable()
    val projectstart = datetime("projectstart").nullable()
    val projectshortname = text("projectshortname").nullable()
    val projectend = datetime("projectend").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val irodsgroupadmin = text("irodsgroupadmin").nullable()
    val irodsgroupidmap = integer("irodsgroupidmap").nullable()
    val projecttyperefid = reference("projecttyperefid", Projecttype)
}
class ProjectEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectEntity>(Project)

    var projectname by Project.projectname
    var projectstart by Project.projectstart
    var projectshortname by Project.projectshortname
    var projectend by Project.projectend
    var created_ts by Project.created_ts
    var modified_ts by Project.modified_ts
    var active by Project.active
    var irodsgroupadmin by Project.irodsgroupadmin
    var irodsgroupidmap by Project.irodsgroupidmap
    var projecttyperefid by Projecttype.id
    var projecttype by ProjecttypeEntity referencedOn Project.projecttyperefid
}