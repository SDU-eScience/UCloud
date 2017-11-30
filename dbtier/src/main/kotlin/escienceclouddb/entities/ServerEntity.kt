package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Server : IntIdTable() {
    val hostname = text("hostname").nullable()
    val lastmodified = datetime("lastmodified")
    val ip = text("ip").nullable()
    val servertext = text("servertext").nullable()
    val health = integer("health").nullable()
}
class ServerEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ServerEntity>(Server)

    var hostname by Server.hostname
    var lastmodified by Server.lastmodified
    var ip by Server.ip
    var servertext by Server.servertext
    var health by Server.health
}