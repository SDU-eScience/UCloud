package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Principal_notification_subscription_type : IntIdTable() {
	val principalnotificationsubscriptiontypename = text("principalnotificationsubscriptiontypename").nullable()
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
	val nosuppress = integer("nosuppress").nullable()
}