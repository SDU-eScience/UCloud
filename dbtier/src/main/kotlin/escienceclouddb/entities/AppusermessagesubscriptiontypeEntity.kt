package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Appusermessagesubscriptiontype : IntIdTable() {
    val appusermessagesubscriptiontypetext = text("appusermessagesubscriptiontypetext").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
}
class AppusermessagesubscriptiontypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<AppusermessagesubscriptiontypeEntity>(Appusermessagesubscriptiontype)

    var appusermessagesubscriptiontypetext by Appusermessagesubscriptiontype.appusermessagesubscriptiontypetext
    var lastmodified by Appusermessagesubscriptiontype.lastmodified
    var active by Appusermessagesubscriptiontype.active
}


