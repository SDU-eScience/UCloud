package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Personsessionhistory : IntIdTable() {
    val lastmodified = datetime("lastmodified")
    val sessionid = text("sessionid").nullable()
    val personrefid = reference("personrefid", Person)
}
class PersonsessionhistoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PersonsessionhistoryEntity>(Personsessionhistory)

    var lastmodified by Personsessionhistory.lastmodified
    var sessionid by Personsessionhistory.sessionid
    var personrefid by Personsessionhistory.personrefid
    var person by PersonEntity referencedOn Projectpersonrel.personrefid
}