package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Org : IntIdTable() {
    val orgfullname = text("orgfullname").nullable()
    val orgshortname = text("orgshortname").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
}
class OrgEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<OrgEntity>(Org)

    var orgfullname by Org.orgfullname
    var orgshortname by Org.orgshortname
    var created_ts by Org.created_ts
    var modified_ts by Org.modified_ts
    var active by Org.active
}