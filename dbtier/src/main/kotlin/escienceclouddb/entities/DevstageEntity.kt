package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Devstage : IntIdTable() {
    val devstagetext = text("devstagetext").nullable()
    val lastmodified = datetime("lastmodified")
}
class DevstageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<DevstageEntity>(Devstage)

    var devstagetext by Devstage.devstagetext
    var lastmodified by Devstage.lastmodified
}