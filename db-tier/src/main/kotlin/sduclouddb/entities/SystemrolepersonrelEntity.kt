package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


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