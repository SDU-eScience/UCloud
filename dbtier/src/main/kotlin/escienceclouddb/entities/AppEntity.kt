

import escienceclouddb.Person
import escienceclouddb.PersonEntity
import escienceclouddb.Projectpersonrel
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object App : IntIdTable() {
    val appdescriptiontext = text("appdescriptiontext").nullable()
    val apptext = text("apptext").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val personrefid = reference("personrefid", Person)


}
class AppEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<AppEntity>(App)

    var appdescriptiontext by App.appdescriptiontext
    var apptext by App.apptext
    var created_ts by App.created_ts
    var modified_ts by App.modified_ts
    var active by App.active
    var personrefid by App.personrefid
    var person by PersonEntity referencedOn Projectpersonrel.personrefid
}