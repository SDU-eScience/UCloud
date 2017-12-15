package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Personemailrel : IntIdTable() {
    val personrefid = reference("personrefid", Person)
    val emailrefid = reference("emailrefid", Email)
    val primaryemail = integer("primaryemail").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
}
class PersonemailrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PersonemailrelEntity>(Personemailrel)
    var emailrefid by EmailEntity referencedOn Personemailrel.emailrefid
    var email by EmailEntity referencedOn Personemailrel.emailrefid
    var primaryemail by Personemailrel.primaryemail
    var created_ts by Personemailrel.created_ts
    var modified_ts by Personemailrel.modified_ts
    var active by Personemailrel.active
    var personrefid by Personemailrel.personrefid
    var person by PersonEntity referencedOn Personemailrel.personrefid
}