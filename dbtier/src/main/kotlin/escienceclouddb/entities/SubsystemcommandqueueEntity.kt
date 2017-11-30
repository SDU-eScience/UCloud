package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Subsystemcommandqueue : IntIdTable() {
    val subsystemcommandstatusrefid  = reference("subsystemcommandstatusrefid ", Subsystemcommandstatus)
    val personsessionhistoryrefid  = reference("personsessionhistoryrefid ", Personsessionhistory)
    val payload = text("payload").nullable()
    val lastmodified = datetime("lastmodified")
    val subsystemcommandrefid  = reference("subsystemcommandrefid ", Subsystemcommand)

}
class SubsystemcommandqueueEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SubsystemcommandqueueEntity>(Subsystemcommandqueue)

    var subsystemcommandstatusrefid by Subsystemcommandqueue.subsystemcommandstatusrefid
    var subsystemcommandstatus by SubsystemcommandstatusEntity referencedOn Subsystemcommandqueue.subsystemcommandstatusrefid
    var personsessionhistoryrefid by Subsystemcommandqueue.personsessionhistoryrefid
    var personsessionhistory by PersonsessionhistoryEntity referencedOn Subsystemcommandqueue.subsystemcommandstatusrefid
    var payload by Subsystemcommandqueue.payload
    var lastmodified by Subsystemcommandqueue.lastmodified
    var subsystemcommandrefid by Subsystemcommandqueue.subsystemcommandrefid
    var subsystemcommand by SubsystemcommandEntity referencedOn Subsystemcommandqueue.subsystemcommandrefid
}