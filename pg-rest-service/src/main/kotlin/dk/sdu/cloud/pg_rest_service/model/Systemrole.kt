package dk.sdu.cloud.pg_rest_service.model
import dk.sdu.cloud.controller.dao.PrincipalEntity
import org.jetbrains.exposed.dao.IntIdTable

object Systemrole : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val principalrefid = Systemrole.reference("principal", Principal)
	val systemroletext = text("systemroletext").nullable()
}