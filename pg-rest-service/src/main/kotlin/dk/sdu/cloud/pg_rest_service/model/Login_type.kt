package dk.sdu.cloud.pg_rest_service.model

import org.jetbrains.exposed.dao.IntIdTable

object Login_type : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
	val login_type_name = text("login_type_name").nullable()
}