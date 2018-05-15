package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Subsystem_command
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Subsystem_commandEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Subsystem_commandEntity>(Subsystem_command)

	var modified_ts by Subsystem_command.modified_ts
	var subsystemcommandcategoryrefid by Subsystem_command.subsystemcommandcategoryrefid
	var marked_for_delete by Subsystem_command.marked_for_delete
	var created_ts by Subsystem_command.created_ts
	var subsystemrefid by Subsystem_command.subsystemrefid
	var payloadmodel by Subsystem_command.payloadmodel
	var implemented by Subsystem_command.implemented
	var kafkatopicname by Subsystem_command.kafkatopicname
	var daoutil by Subsystem_command.daoutil
}