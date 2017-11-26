package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Systemrole : IntIdTable() {
    val lastmodified = datetime("lastmodified")
    val systemroletext = text("systemroletext").nullable()
    val landingpage = text("landingpage").nullable()
    val active = integer("active").nullable()
}
class SystemroleEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SystemroleEntity>(Systemrole)

    var lastmodified by Systemrole.lastmodified
    var systemroletext by Systemrole.systemroletext
    var landingpage by Systemrole.landingpage
    var active by Systemrole.active
}