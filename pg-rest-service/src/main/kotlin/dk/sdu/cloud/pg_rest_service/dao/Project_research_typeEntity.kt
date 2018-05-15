package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Project_research_type
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Project_research_typeEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Project_research_typeEntity>(Project_research_type)

	var modified_ts by Project_research_type.modified_ts
	var marked_for_delete by Project_research_type.marked_for_delete
	var created_ts by Project_research_type.created_ts
	var projectresearchtypetext by Project_research_type.projectresearchtypetext
	var active by Project_research_type.active
}