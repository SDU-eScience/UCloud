package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable

object Data_transfer_detail : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val created_ts = datetime("created_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val data_object_ref_id = text("data_object_ref_id").nullable()
	val data_transfer_header_ref_id = reference("data_transfer_header_ref_id", Data_transfer_header)
	val part_bytes = integer("part_bytes").nullable()
	val part_progress = integer("part_progress").nullable()
	val active = integer("active").nullable()
}



