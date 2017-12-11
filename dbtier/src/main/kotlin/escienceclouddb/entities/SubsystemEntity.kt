package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Subsystem : IntIdTable() {
    val ip_prod = text("ip_prod").nullable()
    val subsystemtext = text("subsystemtext").nullable()
    val ip_test = text("ip_test").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val port_dev = text("port_dev").nullable()
    val health = integer("health").nullable()
    val port_test = text("port_test").nullable()
    val ip_dev = text("ip_dev").nullable()
    val port_prod = text("port_prod").nullable()
}
class SubsystemEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SubsystemEntity>(Subsystem)

    var ip_prod by Subsystem.ip_prod
    var subsystemtext by Subsystem.subsystemtext
    var ip_test by Subsystem.ip_test
    var created_ts by Subsystem.created_ts
    var modified_ts by Subsystem.modified_ts
    var port_dev by Subsystem.port_dev
    var health by Subsystem.health
    var port_test by Subsystem.port_test
    var ip_dev by Subsystem.ip_dev
    var port_prod by Subsystem.port_prod
}