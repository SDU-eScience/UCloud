package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Subsystem
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class SubsystemEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<SubsystemEntity>(Subsystem)

	var modified_ts by Subsystem.modified_ts
	var ip_prod by Subsystem.ip_prod
	var ip_test by Subsystem.ip_test
	var marked_for_delete by Subsystem.marked_for_delete
	var created_ts by Subsystem.created_ts
	var port_dev by Subsystem.port_dev
	var health by Subsystem.health
	var subsystemname by Subsystem.subsystemname
	var port_test by Subsystem.port_test
	var ip_dev by Subsystem.ip_dev
	var port_prod by Subsystem.port_prod
}