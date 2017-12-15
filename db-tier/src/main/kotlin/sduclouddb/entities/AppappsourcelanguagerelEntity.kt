package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Appappsourcelanguagerel : IntIdTable() {
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val appsourcelanguagerefid = reference("appsourcelanguagerefid", Appsourcelanguage)
    val active = integer("active").nullable()
    val apprefid = reference("apprefid", App)

}
class AppappsourcelanguagerelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<AppappsourcelanguagerelEntity>(Appappsourcelanguagerel)

    var created_ts by Appappsourcelanguagerel.created_ts
    var modified_ts by Appappsourcelanguagerel.modified_ts
    var appsourcelanguagerefid by Appappsourcelanguagerel.appsourcelanguagerefid
    var appsourcelanguage by AppsourcelanguageEntity referencedOn Appappsourcelanguagerel.appsourcelanguagerefid
    var active by Appappsourcelanguagerel.active
    var apprefid by Appappsourcelanguagerel.appsourcelanguagerefid
    var app by AppEntity referencedOn Appappsourcelanguagerel.apprefid
}