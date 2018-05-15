package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Project_org_relation
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Project_org_relationEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Project_org_relationEntity>(Project_org_relation)

	var projectrefid by Project_org_relation.projectrefid
	var modified_ts by Project_org_relation.modified_ts
	var marked_for_delete by Project_org_relation.marked_for_delete
	var created_ts by Project_org_relation.created_ts
	var active by Project_org_relation.active
	var orgrefid by Project_org_relation.orgrefid
}