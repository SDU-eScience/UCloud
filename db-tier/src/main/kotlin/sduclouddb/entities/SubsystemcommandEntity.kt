package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Subsystemcommand : IntIdTable() {
    val subsystemcommandcategoryrefid  = reference("subsystemcommandcategoryrefid ", Subsystemcommandcategory)
    val subsystemrefid  = reference("subsystemrefid ", Subsystem)
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val implemented = bool("implemented")
    val kafkatopicname = text("kafkatopicname").nullable()
    val subsystemcommandtext = text("subsystemcommandtext").nullable()
}
class SubsystemcommandEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SubsystemcommandEntity>(Subsystemcommand)

    var subsystemcommandcategoryrefid by Subsystemcommand.subsystemcommandcategoryrefid
    var subsystemcommandcategory by SubsystemcommandcategoryEntity referencedOn Subsystemcommand.subsystemcommandcategoryrefid
    var subsystemrefid by Subsystemcommand.subsystemrefid
    var subsystem by SubsystemEntity referencedOn Subsystemcommand.subsystemrefid
    var created_ts by Subsystemcommand.created_ts
    var modified_ts by Subsystemcommand.modified_ts
    var implemented by Subsystemcommand.implemented
    var kafkatopicname by Subsystemcommand.kafkatopicname
    var subsystemcommandtext by Subsystemcommand.subsystemcommandtext
}