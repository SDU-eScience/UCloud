package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Project_document
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Project_documentEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Project_documentEntity>(Project_document)

	var modified_ts by Project_document.modified_ts
	var projectdocumentfilename by Project_document.projectdocumentfilename
	var marked_for_delete by Project_document.marked_for_delete
	var created_ts by Project_document.created_ts
	var active by Project_document.active
	var projectdocumentbin by Project_document.projectdocumentbin
	var documenttypedescription by Project_document.documenttypedescription
}