package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectpublicationrel : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val publicationrefid =reference("publicationrefid", Publication)
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
}
class ProjectpublicationrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectpublicationrelEntity>(Projectpublicationrel)

    var projectrefid by Projectpublicationrel.projectrefid
    var project by ProjectEntity referencedOn Projectpublicationrel.projectrefid
    var publicationrefid by Projectpublicationrel.publicationrefid
    var publication by PublicationEntity referencedOn Projectpublicationrel.publicationrefid
    var created_ts by Projectpublicationrel.created_ts
    var modified_ts by Projectpublicationrel.modified_ts
    var active by Projectpublicationrel.active
}
