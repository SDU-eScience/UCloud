

import escienceclouddb.Appsourcelanguage
import escienceclouddb.AppsourcelanguageEntity
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Appappsourcelanguagerel : IntIdTable() {
    val lastmodified = datetime("lastmodified")
    val appsourcelanguagerefid = reference("appsourcelanguagerefid", Appsourcelanguage)
    val active = integer("active").nullable()
    val apprefid = reference("apprefid", App)

}
class AppappsourcelanguagerelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<AppappsourcelanguagerelEntity>(Appappsourcelanguagerel)

    var lastmodified by Appappsourcelanguagerel.lastmodified
    var appsourcelanguagerefid by Appappsourcelanguagerel.appsourcelanguagerefid
    var appsourcelanguage by AppsourcelanguageEntity referencedOn Appappsourcelanguagerel.appsourcelanguagerefid
    var active by Appappsourcelanguagerel.active
    var apprefid by Appappsourcelanguagerel.appsourcelanguagerefid
    var app by AppEntity referencedOn Appappsourcelanguagerel.apprefid
}