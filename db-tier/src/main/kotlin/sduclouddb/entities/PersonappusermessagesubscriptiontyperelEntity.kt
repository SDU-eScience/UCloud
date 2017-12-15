package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Personappusermessagesubscriptiontyperel : IntIdTable() {
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val appusermessagesubscriptiontyperefid =  reference("appusermessagesubscriptiontyperefid", Appusermessagesubscriptiontype)
    val personrefid = reference("personrefid", Person)
}
class PersonappusermessagesubscriptiontyperelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PersonappusermessagesubscriptiontyperelEntity>(Personappusermessagesubscriptiontyperel)

    var created_ts by Personappusermessagesubscriptiontyperel.created_ts
    var modified_ts by Personappusermessagesubscriptiontyperel.modified_ts
    var active by Personappusermessagesubscriptiontyperel.active
    var appusermessagesubscriptiontyperefid by Personappusermessagesubscriptiontyperel.appusermessagesubscriptiontyperefid
    var appusermessagesubscriptiontype by AppusermessagesubscriptiontypeEntity referencedOn Personappusermessagesubscriptiontyperel.appusermessagesubscriptiontyperefid
    var personrefid by Personappusermessagesubscriptiontyperel.personrefid
    var person by PersonEntity referencedOn Personappusermessagesubscriptiontyperel.personrefid
}