package dk.sdu.cloud.pg_rest_service.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Email
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class ProjectEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<ProjectEntity>(Email)

	var modified_ts by Email.modified_ts
	var preferredemail by Email.preferredemail
	var marked_for_delete by Email.marked_for_delete
	var created_ts by Email.created_ts
	var principalrefid by Email.principalrefid
	var email by Email.email
}