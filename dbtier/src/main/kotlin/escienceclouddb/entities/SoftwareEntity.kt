package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Software : IntIdTable() {
    val rpms = text("rpms").nullable()
    val softwaretext = text("softwaretext").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val serverrefid  = reference("serverrefid ", Server)
    val devstagerefid  = reference("devstagerefid ", Devstage)
    val downloadurl = text("downloadurl").nullable()
    val yums = text("yums").nullable()
    val ports = text("ports").nullable()
    val version = text("version").nullable()
}
class SoftwareEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SoftwareEntity>(Software)

    var rpms by Software.rpms
    var softwaretext by Software.softwaretext
    var created_ts by Software.created_ts
    var modified_ts by Software.modified_ts
    var serverrefid by Software.serverrefid
    var server by ServerEntity referencedOn Software.serverrefid
    var devstagerefid by Software.devstagerefid
    var devstage by DevstageEntity referencedOn Software.devstagerefid
    var downloadurl by Software.downloadurl
    var yums by Software.yums
    var ports by Software.ports
    var version by Software.version
}
