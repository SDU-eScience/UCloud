package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Logintype : IntIdTable() {
    val lastmodified = datetime("lastmodified")
    val logintypetext = text("logintypetext").nullable()
    val active = integer("active").nullable()
}
class LogintypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<LogintypeEntity>(Logintype)

    var lastmodified by Logintype.lastmodified
    var logintypetext by Logintype.logintypetext
    var active by Logintype.active
}