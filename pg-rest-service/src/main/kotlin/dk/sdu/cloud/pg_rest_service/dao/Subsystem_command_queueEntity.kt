package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Subsystem_command_queue
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Subsystem_command_queueEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Subsystem_command_queueEntity>(Subsystem_command_queue)

	var modified_ts by Subsystem_command_queue.modified_ts
	var subsystemcommandstatusrefid by Subsystem_command_queue.subsystemcommandstatusrefid
	var marked_for_delete by Subsystem_command_queue.marked_for_delete
	var payload by Subsystem_command_queue.payload
	var created_ts by Subsystem_command_queue.created_ts
	var subsystemcommandrefid by Subsystem_command_queue.subsystemcommandrefid
	var personjwthistoryrefid by Subsystem_command_queue.personjwthistoryrefid
}