package dk.sdu.cloud.pg_rest_service.dao
import dk.sdu.cloud.pg_rest_service.model.App
import dk.sdu.cloud.pg_rest_service.model.Principal
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class AppEntity(id: EntityID<Int>): IntEntity(id) {
	companion object: IntEntityClass<AppEntity>(App)

	var modified_ts  by App.modified_ts
	var created_ts by App.created_ts
	var marked_for_delete by App.marked_for_delete
	var app_name by App.app_name
	var app_description_text by App.app_description_text
	var active by App.active
	var prepped by App.prepped
	var git_url by App.git_url
	var cwl_file by App.cwl_file
	var principal_ref_id by Principal referrersOn App.principal_ref_id

}