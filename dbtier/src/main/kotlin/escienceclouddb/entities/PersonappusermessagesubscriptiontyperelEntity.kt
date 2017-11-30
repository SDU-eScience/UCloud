package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Personappusermessagesubscriptiontyperel : IntIdTable() {
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val appusermessagesubscriptiontyperefid =  reference("appusermessagesubscriptiontyperefid", Appusermessagesubscriptiontype)
    val personrefid = reference("personrefid", Person)
}
class PersonappusermessagesubscriptiontyperelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PersonappusermessagesubscriptiontyperelEntity>(Personappusermessagesubscriptiontyperel)

    var lastmodified by Personappusermessagesubscriptiontyperel.lastmodified
    var active by Personappusermessagesubscriptiontyperel.active
    var appusermessagesubscriptiontyperefid by Personappusermessagesubscriptiontyperel.appusermessagesubscriptiontyperefid
    var appusermessagesubscriptiontype by AppusermessagesubscriptiontypeEntity referencedOn Personappusermessagesubscriptiontyperel.appusermessagesubscriptiontyperefid
    var personrefid by Personappusermessagesubscriptiontyperel.personrefid
    var person by PersonEntity referencedOn Personappusermessagesubscriptiontyperel.personrefid
}