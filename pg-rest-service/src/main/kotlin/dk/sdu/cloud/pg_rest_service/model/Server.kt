package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Server : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val hostname = text("hostname").nullable()
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val ip = text("ip").nullable()
	val servername = text("servername").nullable()
	val health = integer("health").nullable()
}