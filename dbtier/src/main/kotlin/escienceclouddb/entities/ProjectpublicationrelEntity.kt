package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectpublicationrel : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val publicationrefid =reference("publicationrefid", Publication)
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
}
class ProjectpublicationrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectpublicationrelEntity>(Projectpublicationrel)

    var projectrefid by Projectpublicationrel.projectrefid
    var project by ProjectEntity referencedOn Projectpublicationrel.projectrefid
    var publicationrefid by Projectpublicationrel.publicationrefid
    var publication by PublicationEntity referencedOn Projectpublicationrel.publicationrefid
    var lastmodified by Projectpublicationrel.lastmodified
    var active by Projectpublicationrel.active
}
