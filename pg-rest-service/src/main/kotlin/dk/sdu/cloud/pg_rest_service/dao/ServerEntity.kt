package dk.sdu.cloud.controller.dao
import dk.sdu.cloud.pg_rest_service.model.Server
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class ServerEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<ServerEntity>(Server)

	var modified_ts by Server.modified_ts
	var hostname by Server.hostname
	var marked_for_delete by Server.marked_for_delete
	var created_ts by Server.created_ts
	var ip by Server.ip
	var servername by Server.servername
	var health by Server.health
}