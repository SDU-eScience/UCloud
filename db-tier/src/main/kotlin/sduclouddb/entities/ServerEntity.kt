package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Server : IntIdTable() {
    val hostname = text("hostname").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val ip = text("ip").nullable()
    val servername = text("servername").nullable()
    val health = integer("health").nullable()
}
class ServerEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ServerEntity>(Server)

    var hostname by Server.hostname
    var created_ts by Server.created_ts
    var modified_ts by Server.modified_ts
    var ip by Server.ip
    var servername by Server.servername
    var health by Server.health
}