package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Dataobject_file_extension
import dk.sdu.cloud.pg_rest_service.model.Devstage
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass

class Dataobject_file_extensionEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Dataobject_file_extensionEntity>(Dataobject_file_extension)
	var modified_ts by Dataobject_file_extension.modified_ts
	var marked_for_delete by Dataobject_file_extension.marked_for_delete
	var created_ts by Dataobject_file_extension.created_ts
	var file_extension_name by Dataobject_file_extension.file_extension_name
	var file_extension_desc by Dataobject_file_extension.file_extension_desc
	var mime_type by Dataobject_file_extension.mime_type




}