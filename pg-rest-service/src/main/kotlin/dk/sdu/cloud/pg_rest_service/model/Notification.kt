package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable

object Notification : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val notification_text = text("notification_text")
	val marked_for_delete = integer("marked_for_delete")
	val created_ts = datetime("created_ts")
	//val meta = json("meta").nullable()
	val viewed = integer("viewed")
	val principal_ref_id = reference("principal", Principal)
}