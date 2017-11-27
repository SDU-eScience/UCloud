package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Subsystemcommandstatus : IntIdTable() {
    val lastmodified = datetime("lastmodified")
    val subsystemcommandstatustext = text("subsystemcommandstatustext").nullable()
}
class SubsystemcommandstatusEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SubsystemcommandstatusEntity>(Subsystemcommandstatus)

    var lastmodified by Subsystemcommandstatus.lastmodified
    var subsystemcommandstatustext by Subsystemcommandstatus.subsystemcommandstatustext
}