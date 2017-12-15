package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Appsourcelanguage : IntIdTable() {
    val appsourcelanguagename = text("appsourcelanguagename").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
}
class AppsourcelanguageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<AppsourcelanguageEntity>(Appsourcelanguage)

    var appsourcelanguagename by Appsourcelanguage.appsourcelanguagename
    var created_ts by Appsourcelanguage.created_ts
    var modified_ts by Appsourcelanguage.modified_ts
    var active by Appsourcelanguage.active
}