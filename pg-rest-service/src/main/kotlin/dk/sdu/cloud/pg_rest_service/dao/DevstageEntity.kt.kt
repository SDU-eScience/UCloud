package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Dataobject_file_extension
import dk.sdu.cloud.pg_rest_service.model.Devstage
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass

class DevstageEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<DevstageEntity>(Devstage)
	var modified_ts by Devstage.modified_ts
	var marked_for_delete by Devstage.marked_for_delete
	var created_ts by Devstage.created_ts
	var devstage_name by Devstage.devstage_name






}