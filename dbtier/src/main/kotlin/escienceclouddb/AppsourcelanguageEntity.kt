package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Appsourcelanguage : IntIdTable() {
    val appsourcelanguagetext = text("appsourcelanguagetext").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
}
class AppsourcelanguageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<AppsourcelanguageEntity>(Appsourcelanguage)

    var appsourcelanguagetext by Appsourcelanguage.appsourcelanguagetext
    var lastmodified by Appsourcelanguage.lastmodified
    var active by Appsourcelanguage.active
}