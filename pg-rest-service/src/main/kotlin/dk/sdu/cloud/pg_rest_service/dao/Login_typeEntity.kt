package dk.sdu.cloud.pg_rest_service.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Login_type
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Login_typeEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Login_typeEntity>(Login_type)

	var modified_ts by Login_type.modified_ts
	var marked_for_delete by Login_type.marked_for_delete
	var created_ts by Login_type.created_ts
	var active by Login_type.active
	var login_type_name by Login_type.login_type_name
}