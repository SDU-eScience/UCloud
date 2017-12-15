package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Subsystemcommandstatus : IntIdTable() {
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val subsystemcommandstatustext = text("subsystemcommandstatustext").nullable()
}
class SubsystemcommandstatusEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SubsystemcommandstatusEntity>(Subsystemcommandstatus)

    var created_ts by Subsystemcommandstatus.created_ts
    var modified_ts by Subsystemcommandstatus.modified_ts
    var subsystemcommandstatustext by Subsystemcommandstatus.subsystemcommandstatustext
}