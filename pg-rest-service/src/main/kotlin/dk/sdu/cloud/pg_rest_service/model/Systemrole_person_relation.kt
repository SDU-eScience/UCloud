package dk.sdu.cloud.pg_rest_service.model
import dk.sdu.cloud.controller.dao.Systemrole_person_relationEntity
import dk.sdu.cloud.controller.model.Principal
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.transactions.transaction


object Systemrole_person_relation : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val systemrolerefid = reference("systemrole", Systemrole)
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
	val principalrefid = Notification.reference("principal", Principal)
}fun getsystemrole_person_relationEntityList():List<Systemrole_person_relationEntity> {
	return transaction {
		(Systemrole_person_relationEntity).find { Systemrole_person_relation.active eq 1 }.toList()
	}
}