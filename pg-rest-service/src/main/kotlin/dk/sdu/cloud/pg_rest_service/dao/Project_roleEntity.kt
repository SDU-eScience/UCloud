import dk.sdu.cloud.pg_rest_service.model.Project_role
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass



class Project_roleEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Project_roleEntity>(Project_role)

	var modified_ts by Project_role.modified_ts
	var projectrolename by Project_role.projectrolename
	var marked_for_delete by Project_role.marked_for_delete
	var created_ts by Project_role.created_ts
	var active by Project_role.active
}