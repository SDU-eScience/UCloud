package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Publication : IntIdTable() {
    val publicationextlink = text("publicationextlink").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val publicationtitle = text("publicationtitle").nullable()
    val publicationdate = datetime("publicationdate").nullable()
}
class PublicationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PublicationEntity>(Publication)

    var publicationextlink by Publication.publicationextlink
    var lastmodified by Publication.lastmodified
    var active by Publication.active
    var publicationtitle by Publication.publicationtitle
    var publicationdate by Publication.publicationdate
}