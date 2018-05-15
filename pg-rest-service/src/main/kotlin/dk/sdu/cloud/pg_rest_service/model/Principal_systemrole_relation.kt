package dk.sdu.cloud.pg_rest_service.model
import dk.sdu.cloud.controller.model.Principal
import org.jetbrains.exposed.dao.IntIdTable


object Principal_systemrole_relation : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	//val systemrolerefid = Principal.reference("system_role", Systemrole)
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
	val principalrefid = Principal_systemrole_relation.reference("principal", Principal)
}