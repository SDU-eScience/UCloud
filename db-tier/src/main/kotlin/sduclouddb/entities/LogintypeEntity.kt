package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Logintype : IntIdTable() {
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val logintypetext = text("logintypetext").nullable()
    val active = integer("active").nullable()
}
class LogintypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<LogintypeEntity>(Logintype)

    var created_ts by Logintype.created_ts
    var modified_ts by Logintype.modified_ts
    var logintypetext by Logintype.logintypetext
    var active by Logintype.active
}