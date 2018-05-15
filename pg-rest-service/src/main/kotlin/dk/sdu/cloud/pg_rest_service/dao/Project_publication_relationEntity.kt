package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Project_principal_relation
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Project_principal_relationEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Project_principal_relationEntity>(Project_principal_relation)

	var projectrefid by Project_principal_relation.projectrefid
	var modified_ts by Project_principal_relation.modified_ts
	var projectrolerefid by Project_principal_relation.projectrolerefid
	var marked_for_delete by Project_principal_relation.marked_for_delete
	var created_ts by Project_principal_relation.created_ts
	var active by Project_principal_relation.active
	var personrefid by Project_principal_relation.personrefid
}