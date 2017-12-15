package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable



object Projecteventcalendar : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val eventend = datetime("eventend").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val eventname = text("eventname").nullable()
    val eventstart = datetime("eventstart").nullable()
    val personrefid = reference("personrefid", Person)
}
class ProjecteventcalendarEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjecteventcalendarEntity>(Projecteventcalendar)
    var projectrefid by Projecteventcalendar.projectrefid
    var project by ProjectEntity referencedOn Projecteventcalendar.projectrefid
    var eventend by Projecteventcalendar.eventend
    var created_ts by Projecteventcalendar.created_ts
    var modified_ts by Projecteventcalendar.modified_ts
    var active by Projecteventcalendar.active
    var eventname by Projecteventcalendar.eventname
    var eventstart by Projecteventcalendar.eventstart
    var personrefid by Projecteventcalendar.personrefid
    var person by PersonEntity referencedOn Projecteventcalendar.personrefid
}