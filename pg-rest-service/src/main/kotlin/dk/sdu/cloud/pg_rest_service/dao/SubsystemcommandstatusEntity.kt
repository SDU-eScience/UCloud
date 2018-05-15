package dk.sdu.cloud.pg_rest_service.controller.dao
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class SubsystemcommandstatusEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<SubsystemcommandstatusEntity>(Subsystemcommandstatus)

	var modified_ts by Subsystemcommandstatus.modified_ts
	var marked_for_delete by Subsystemcommandstatus.marked_for_delete
	var created_ts by Subsystemcommandstatus.created_ts
	var active by Subsystemcommandstatus.active
	var subsystemcommandstatusname by Subsystemcommandstatus.subsystemcommandstatusname
}