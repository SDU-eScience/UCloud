package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Project_publication_relation : IntIdTable() {
	val projectrefid = Project_publication_relation.reference("project", Project)
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val publicationrefid = Project_publication_relation.reference("publication", Publication)
	val active = integer("active").nullable()
}