package dk.sdu.cloud.pg_rest_service.model
import dk.sdu.cloud.controller.model.Principal
import org.jetbrains.exposed.dao.IntIdTable


object Project_principal_relation : IntIdTable() {
	val projectrefid = Project_principal_relation.reference("project", Project)
	val modified_ts = datetime("modified_ts")
	val projectrolerefid = Project_principal_relation.reference("projectrole", Project_role)
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
	val principalrefid = Notification.reference("principal", Principal)
}