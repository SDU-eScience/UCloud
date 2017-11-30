package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectdocument : IntIdTable() {
    val projectdocumentfilename = text("projectdocumentfilename").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val projectdocumentbin = blob("projectdocumentbin").nullable()
    val documenttypedescription = text("documenttypedescription").nullable()
}
class ProjectdocumentEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectdocumentEntity>(Projectdocument)

    var projectdocumentfilename by Projectdocument.projectdocumentfilename
    var lastmodified by Projectdocument.lastmodified
    var active by Projectdocument.active
    var projectdocumentbin by Projectdocument.projectdocumentbin
    var documenttypedescription by Projectdocument.documenttypedescription
}