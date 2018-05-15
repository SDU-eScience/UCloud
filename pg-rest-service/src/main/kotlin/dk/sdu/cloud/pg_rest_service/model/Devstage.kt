package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable

object Devstage : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete")
	val devstage_name = text("devstage_name")
	val created_ts = datetime("created_ts")
}


