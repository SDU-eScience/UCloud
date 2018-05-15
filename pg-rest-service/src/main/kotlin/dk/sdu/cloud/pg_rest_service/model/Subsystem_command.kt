package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Subsystem_command : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val subsystemcommandcategoryrefid = Subsystem_command.reference("subsystemcommandcategory",Subsystemcommandcategory).nullable()
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val subsystemrefid = Subsystem_command.reference("subsystem", Subsystem)
	val payloadmodel = text("payloadmodel").nullable()
	val implemented = bool("implemented")
	val kafkatopicname = text("kafkatopicname").nullable()
	val daoutil = text("daoutil").nullable()
}