package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Personsessionhistory : IntIdTable() {
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val sessionid = text("sessionid").nullable()
    val jwt = text("jwt").nullable()
    val personrefid = reference("personrefid", Person)
}
class PersonsessionhistoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PersonsessionhistoryEntity>(Personsessionhistory)

    var created_ts by Personsessionhistory.created_ts
    var modified_ts by Personsessionhistory.modified_ts
    var sessionid by Personsessionhistory.sessionid
    var jwt by Personsessionhistory.jwt
    var personrefid by Personsessionhistory.personrefid
    var person by PersonEntity referencedOn Projectpersonrel.personrefid
}