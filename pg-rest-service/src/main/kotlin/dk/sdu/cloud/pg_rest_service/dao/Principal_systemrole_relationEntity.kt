package dk.sdu.cloud.controller.dao
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Principal_systemrole_relationEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Principal_systemrole_relationEntity>(Principal_systemrole_relation)

	var modified_ts by Principal_systemrole_relation.modified_ts
	var systemrolerefid by Principal_systemrole_relation.systemrolerefid
	var marked_for_delete by Principal_systemrole_relation.marked_for_delete
	var created_ts by Principal_systemrole_relation.created_ts
	var active by Principal_systemrole_relation.active
	var principalrefid by Principal_systemrole_relation.principalrefid
}