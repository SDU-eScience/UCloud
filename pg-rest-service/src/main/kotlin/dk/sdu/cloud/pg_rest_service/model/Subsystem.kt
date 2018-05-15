package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Subsystem : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val ip_prod = text("ip_prod").nullable()
	val ip_test = text("ip_test").nullable()
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val port_dev = text("port_dev").nullable()
	val health = integer("health").nullable()
	val subsystemname = text("subsystemname").nullable()
	val port_test = text("port_test").nullable()
	val ip_dev = text("ip_dev").nullable()
	val port_prod = text("port_prod").nullable()
}