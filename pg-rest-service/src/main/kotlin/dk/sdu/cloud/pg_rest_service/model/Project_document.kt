package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Project_document : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val projectdocumentfilename = text("projectdocumentfilename").nullable()
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
	val projectdocumentbin = blob("projectdocumentbin").nullable()
	val documenttypedescription = text("documenttypedescription").nullable()
}