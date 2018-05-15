package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Subsystem_command_queue : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val subsystemcommandstatusrefid = Subsystem_command_queue.reference("subsystemcommandstatus",Subsystemcommandstatus)
	val marked_for_delete = integer("marked_for_delete").nullable()
	val payload = text("payload").nullable()
	val created_ts = datetime("created_ts")
	val subsystemcommandrefid = Subsystem_command_queue.reference("subsystemcommand", Subsystem_command)
	val personjwthistoryrefid = integer("personjwthistoryrefid").nullable()
}