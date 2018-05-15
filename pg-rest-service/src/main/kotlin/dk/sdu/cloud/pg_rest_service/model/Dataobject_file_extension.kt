package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable

object Dataobject_file_extension : IntIdTable() {
	val file_extension_name = text("file_extension_name").nullable()
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
	val mime_type = text("mime_type").nullable()
	val file_extension_desc = text("file_extension_desc").nullable()
}