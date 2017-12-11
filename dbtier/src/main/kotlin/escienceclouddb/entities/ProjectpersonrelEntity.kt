package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectpersonrel : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val projectrolerefid = reference("projectrolerefid", Projectrole)
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val personrefid = reference("personrefid", Person)
}
class ProjectpersonrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectpersonrelEntity>(Projectpersonrel)

    var projectrefid by Projectpersonrel.projectrefid
    var project by ProjectEntity referencedOn Projectpersonrel.projectrefid
    var projectrolerefid by Projectpersonrel.projectrolerefid
    var projectrole by ProjectroleEntity referencedOn Projectpersonrel.projectrolerefid
    var created_ts by Projectpersonrel.created_ts
    var modified_ts by Projectpersonrel.modified_ts
    var active by Projectpersonrel.active
    var personrefid by Projectpersonrel.personrefid
    var person by PersonEntity referencedOn Projectpersonrel.personrefid
}