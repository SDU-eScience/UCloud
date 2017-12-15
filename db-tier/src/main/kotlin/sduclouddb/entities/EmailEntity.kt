package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Email : IntIdTable() {
    val email = text("email").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
}
class EmailEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<OrgEntity>(Org)

    var email by Email.email
    var created_ts by Email.created_ts
    var modified_ts by Email.modified_ts
    var active by Email.active
}