package dk.sdu.cloud.pg_rest_service.model
import dk.sdu.cloud.pg_rest_service.model.App.nullable
import org.jetbrains.exposed.dao.IntIdTable

object Data_transfer_header : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val total_bytes = integer("total_bytes").nullable()
	val total_progress = integer("total_progress").nullable()
	val data_transfer_type_ref_id = reference("data_transfer_type_ref_id",Data_transfer_type)
	val data_transfer_state_ref_id = reference("data_transfer_state_ref_id",Data_transfer_state)
	val principal_ref_id = reference("principal", Principal).nullable()
	val active = integer("active").nullable()
}






