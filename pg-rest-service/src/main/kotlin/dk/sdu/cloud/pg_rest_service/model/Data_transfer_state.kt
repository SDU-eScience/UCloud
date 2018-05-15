package dk.sdu.cloud.pg_rest_service.model

import org.jetbrains.exposed.dao.IntIdTable

object Data_transfer_state : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val data_transfer_state_name = text("data_transfer_state_name")
	val active = integer("active").nullable()
}