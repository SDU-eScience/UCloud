package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Publication : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val created_ts = datetime("created_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val publicationname = text("publicationname").nullable()
	val publicationdate = datetime("publicationdate")
	val publicationextlink = datetime("publicationextlink")
	val active = integer("active").nullable()
}







