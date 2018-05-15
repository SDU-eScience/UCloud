package dk.sdu.cloud.controller.dao

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Systemrole_person_relationEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Systemrole_person_relationEntity>(Systemrole_person_relation)

	var modified_ts by Systemrole_person_relation.modified_ts
	var systemrolerefid by Systemrole_person_relation.systemrolerefid
	var marked_for_delete by Systemrole_person_relation.marked_for_delete
	var created_ts by Systemrole_person_relation.created_ts
	var active by Systemrole_person_relation.active
	var personrefid by Systemrole_person_relation.personrefid
}