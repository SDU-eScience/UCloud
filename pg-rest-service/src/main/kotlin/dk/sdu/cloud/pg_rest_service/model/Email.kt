package dk.sdu.cloud.pg_rest_service.model

import org.jetbrains.exposed.dao.IntIdTable

object Email : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val preferred_email = integer("preferred_email")
	val marked_for_delete = integer("marked_for_delete")
	val created_ts = datetime("created_ts")
	val principal_ref_id = reference("principal", Principal)
	val email = text("email").nullable()
}