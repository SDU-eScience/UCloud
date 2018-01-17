package dk.sdu.cloud.person.api

import org.jetbrains.exposed.dao.*

object Logintype : IntIdTable() {
    val logintypename = text("logintypename")
    val active = integer("active")
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val markedfordelete = integer("markedfordelete")

}

class LogintypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<LogintypeEntity>(Logintype)
    var logintypename by Logintype.logintypename
    var active by Logintype.active
    var created_ts by Logintype.created_ts
    var modified_ts by Logintype.modified_ts

}

object Systemrole : IntIdTable() {
    val systemrolename = text("systemrolename").nullable()
    val landingpage = text("landingpage").nullable()
    val active = integer("active").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val markedfordelete = integer("markedfordelete").nullable()
}
class SystemroleEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SystemroleEntity>(Systemrole)
    var systemrolename by Systemrole.systemrolename
    var landingpage by Systemrole.landingpage
    var active by Systemrole.active
    var created_ts by Systemrole.created_ts
    var modified_ts by Systemrole.modified_ts
    var markedfordelete by Systemrole.markedfordelete
}

object Personjwthistory : IntIdTable() {
    val jwt = text("jwt").nullable()
    val personrefid = reference("personrefid", Person)
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
}
class PersonjwthistoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PersonjwthistoryEntity>(Personjwthistory)
    var jwt by Personjwthistory.jwt
    var personrefid by Personjwthistory.personrefid
    var created_ts by Personjwthistory.created_ts
    var modified_ts by Personjwthistory.modified_ts
    var markedfordelete by Person.markedfordelete
}

object Person : IntIdTable() {
    val personjwthistoryrefid = reference("personjwthistoryrefid", Personjwthistory)
    val persontitle = text("persontitle").nullable()
    val active = integer("active").nullable()
    val orcid = text("orcid").nullable()
    val personfirstname = text("personfirstname").nullable()
    val personmiddlename = text("personmiddlename").nullable()
    val personlastname = text("personlastname").nullable()
    val fullname = text("fullname").nullable()
    val personphoneno = text("personphoneno").nullable()
    val logintyperefid = reference("logintyperefid", Logintype)
    val latitude = decimal("latitude", 10, 5).nullable()
    val longitude = decimal("longitude", 10, 5).nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val markedfordelete = integer("markedfordelete").nullable()
}
class PersonEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<PersonEntity>(Person)
    var personjwthistoryrefid by Person.personjwthistoryrefid
    var personjwthistory by PersonjwthistoryEntity referencedOn Person.personjwthistoryrefid
    var persontitle by Person.persontitle
    var active by Person.active
    var orcid by Person.orcid
    var personfirstname by Person.personfirstname
    var personmiddlename by Person.personmiddlename
    var personlastname by Person.personlastname
    var fullname by Person.fullname
    var personphoneno by Person.personphoneno
    var logintyperefid by Person.logintyperefid
    var logintype by LogintypeEntity referencedOn Person.logintyperefid
    var latitude by Person.latitude
    var longitude by Person.longitude
    var created_ts by Person.created_ts
    var modified_ts by Person.modified_ts
    var markedfordelete by Person.markedfordelete
}

object Systemrolepersonrel : IntIdTable() {
    val systemrolerefid = reference("systemrolerefid", Systemrole)
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val personrefid = reference("personrefid", Person)
}
class SystemrolepersonrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SystemrolepersonrelEntity>(Systemrolepersonrel)

    var systemrolerefid by Systemrolepersonrel.systemrolerefid
    var systemrole by SystemroleEntity referencedOn Systemrolepersonrel.systemrolerefid
    var created_ts by Systemrolepersonrel.created_ts
    var modified_ts by Systemrolepersonrel.modified_ts
    var active by Systemrolepersonrel.active
    var personrefid by Systemrolepersonrel.personrefid
    var person by PersonEntity referencedOn Systemrolepersonrel.personrefid
}


data class PersonMember(val user: Long, val role: SystemRole)

data class PersonWithMembers(val project: Person, val members: List<PersonMember>)

enum class SystemRole(val roleName: String) {
    LEADER("Leader"),
    TEAM_MEMBER("Team member"),
    ADMIN("Admin"),
    DATA_FACILITATOR("Data facilitator"),
    APP_DESIGNER("App Designer"),
    UNKNOWN("Unknown")
}

enum class Org(val typeName: String) {
    ORDINARY("Ordinary"),
    TEST("Test project"),
    DEVELOPMENT("Development"),
    UNKNOWN("Unknown")
}