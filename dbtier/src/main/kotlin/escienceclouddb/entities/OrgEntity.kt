package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Org : IntIdTable() {
    val orgfullname = text("orgfullname").nullable()
    val orgshortname = text("orgshortname").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
}
class OrgEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<OrgEntity>(Org)

    var orgfullname by Org.orgfullname
    var orgshortname by Org.orgshortname
    var lastmodified by Org.lastmodified
    var active by Org.active
}