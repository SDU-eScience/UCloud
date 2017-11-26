package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Systemrolepersonrel : IntIdTable() {
    val systemrolerefid = reference("systemrolerefid", Systemrole)
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val personrefid = reference("personrefid", Person)
}
class SystemrolepersonrelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SystemrolepersonrelEntity>(Systemrolepersonrel)

    var systemrolerefid by Systemrolepersonrel.systemrolerefid
    var systemrole by SystemroleEntity referencedOn Systemrolepersonrel.systemrolerefid
    var lastmodified by Systemrolepersonrel.lastmodified
    var active by Systemrolepersonrel.active
    var personrefid by Systemrolepersonrel.personrefid
    var person by PersonEntity referencedOn Systemrolepersonrel.personrefid
}