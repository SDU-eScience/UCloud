package dk.sdu.cloud.pg_rest_service.controller.dao
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class PublicationEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<PublicationEntity>(Publication)

	var modified_ts by Publication.modified_ts
	var marked_for_delete by Publication.marked_for_delete
	var created_ts by Publication.created_ts
	val publicationname = Publication.publicationname
	val publicationdate = Publication.publicationdate
	val publicationextlink = Publication.publicationextlink
	val active = Publication.active
}