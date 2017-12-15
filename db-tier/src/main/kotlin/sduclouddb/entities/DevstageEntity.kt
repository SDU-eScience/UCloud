package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Devstage : IntIdTable() {
    val devstagename = text("devstagename").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
}
class DevstageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<DevstageEntity>(Devstage)

    var devstagename by Devstage.devstagename
    var created_ts by Devstage.created_ts
    var modified_ts by Devstage.modified_ts
}