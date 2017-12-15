package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectdocument : IntIdTable() {
    val projectdocumentfilename = text("projectdocumentfilename").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val projectdocumentbin = blob("projectdocumentbin").nullable()
    val documenttypedescription = text("documenttypedescription").nullable()
}
class ProjectdocumentEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectdocumentEntity>(Projectdocument)

    var projectdocumentfilename by Projectdocument.projectdocumentfilename
    var created_ts by Projectdocument.created_ts
    var modified_ts by Projectdocument.modified_ts
    var active by Projectdocument.active
    var projectdocumentbin by Projectdocument.projectdocumentbin
    var documenttypedescription by Projectdocument.documenttypedescription
}