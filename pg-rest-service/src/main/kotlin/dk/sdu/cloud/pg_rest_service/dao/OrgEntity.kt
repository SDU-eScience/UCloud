package dk.sdu.cloud.controller.dao

import dk.sdu.cloud.pg_rest_service.model.Org
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass




class OrgEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<OrgEntity>(Org)

	var modified_ts by Org.modified_ts
	var marked_for_delete by Org.marked_for_delete
	var created_ts by Org.created_ts
	var orgfullname by Org.orgfullname
	var orgshortname by Org.orgshortname
	var active by Org.active
}