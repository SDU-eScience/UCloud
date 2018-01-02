package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Personjwthistory : IntIdTable() {
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val jwt = text("jwt").nullable()
    val personrefid = reference("personrefid", Person)
}
class PersonjwthistoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PersonjwthistoryEntity>(Personjwthistory)

    var created_ts by Personjwthistory.created_ts
    var modified_ts by Personjwthistory.modified_ts
    var jwt by Personjwthistory.jwt
    var personrefid by Personjwthistory.personrefid
    var person by PersonEntity referencedOn Projectpersonrel.personrefid
}