package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Appusermessagesubscriptiontype : IntIdTable() {
    val appusermessagesubscriptiontypename = text("appusermessagesubscriptiontypename").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
}
class AppusermessagesubscriptiontypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<AppusermessagesubscriptiontypeEntity>(Appusermessagesubscriptiontype)

    var appusermessagesubscriptiontypename by Appusermessagesubscriptiontype.appusermessagesubscriptiontypename
    var created_ts by Appusermessagesubscriptiontype.created_ts
    var modified_ts by Appusermessagesubscriptiontype.modified_ts
    var active by Appusermessagesubscriptiontype.active
}


