package dk.sdu.cloud.controller.dao
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Subsystem_command_categoryEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Subsystem_command_categoryEntity>(Subsystem_command_category)

	var modified_ts by Subsystem_command_category.modified_ts
	var marked_for_delete by Subsystem_command_category.marked_for_delete
	var created_ts by Subsystem_command_category.created_ts
	var subsystemcommandcategorytext by Subsystem_command_category.subsystemcommandcategorytext
}