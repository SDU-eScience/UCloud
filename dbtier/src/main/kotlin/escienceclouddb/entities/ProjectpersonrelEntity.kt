package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Projectpersonrel : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val projectrolerefid = reference("projectrolerefid", Projectrole)
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val personrefid = reference("personrefid", Person)
}
class ProjectpersonrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjectpersonrelEntity>(Projectpersonrel)

    var projectrefid by Projectpersonrel.projectrefid
    var project by ProjectEntity referencedOn Projectpersonrel.projectrefid
    var projectrolerefid by Projectpersonrel.projectrolerefid
    var projectrole by ProjectroleEntity referencedOn Projectpersonrel.projectrolerefid
    var lastmodified by Projectpersonrel.lastmodified
    var active by Projectpersonrel.active
    var personrefid by Projectpersonrel.personrefid
    var person by PersonEntity referencedOn Projectpersonrel.personrefid
}