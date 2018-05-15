package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Email
import dk.sdu.cloud.pg_rest_service.model.Principal
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class EmailEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<EmailEntity>(Email)

	var modified_ts by Email.modified_ts
	var preferred_email by Email.preferred_email
	var marked_for_delete by Email.marked_for_delete
	var created_ts by Email.created_ts
	var principal_refid by Principal referrersOn Email.principal_ref_id
	var email by Email.email
}