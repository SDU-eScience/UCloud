package escienceclouddb

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.dao.*


object Person : IntIdTable() {
    val personsessionhistoryrefid = reference("personsessionhistoryrefid", Personsessionhistory)
    val personmiddlename = text("personmiddlename").nullable()
    val persontitle = text("persontitle").nullable()
    val latitude = decimal("latitude", 10, 5).nullable()
    val latestsessionid = text("latestsessionid").nullable()
    val pw = text("pw").nullable()
    val active = integer("active").nullable()
    val orcid = text("orcid").nullable()
    val irodsuseridmap = integer("irodsuseridmap").nullable()
    val personsessionhistory = integer("personsessionhistory").nullable()
    val personfirstname = text("personfirstname").nullable()
    val irodsusername = text("irodsusername").nullable()
    val personlastname = text("personlastname").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val personphoneno = text("personphoneno").nullable()
    val personworkemail = text("personworkemail").nullable()
    val fullname = text("name").nullable()
    val logintyperefid = reference("logintyperefid", Logintype)
    val longitude = decimal("longitude", 10, 5).nullable()
}
class PersonEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PersonEntity>(Person)

    var personsessionhistoryrefid by Person.personsessionhistoryrefid
    var personsessionhistory by PersonsessionhistoryEntity referencedOn Person.personsessionhistoryrefid
    var personmiddlename by Person.personmiddlename
    var persontitle by Person.persontitle
    var latitude by Person.latitude
    var latestsessionid by Person.latestsessionid
    var pw by Person.pw
    var active by Person.active
    var orcid by Person.orcid
    var irodsuseridmap by Person.irodsuseridmap
    var personfirstname by Person.personfirstname
    var irodsusername by Person.irodsusername
    var personlastname by Person.personlastname
    var created_ts by Person.created_ts
    var modified_ts by Person.modified_ts
    var personphoneno by Person.personphoneno
    var personworkemail by Person.personworkemail
    var fullname by Person.fullname
    var logintyperefid by Person.logintyperefid
    var logintype by LogintypeEntity referencedOn Person.logintyperefid
    var longitude by Person.longitude
}