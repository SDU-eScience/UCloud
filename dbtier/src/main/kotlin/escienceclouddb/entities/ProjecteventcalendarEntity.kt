package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Projecteventcalendar : IntIdTable() {
    val projectrefid = reference("projectrefid", Project)
    val eventend = datetime("eventend").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val eventtext = text("eventtext").nullable()
    val eventstart = datetime("eventstart").nullable()
    val personrefid = reference("personrefid", Person)
}
class ProjecteventcalendarEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<ProjecteventcalendarEntity>(Projecteventcalendar)
    var projectrefid by Projecteventcalendar.projectrefid
    var project by ProjectEntity referencedOn Projecteventcalendar.projectrefid
    var eventend by Projecteventcalendar.eventend
    var lastmodified by Projecteventcalendar.lastmodified
    var active by Projecteventcalendar.active
    var eventtext by Projecteventcalendar.eventtext
    var eventstart by Projecteventcalendar.eventstart
    var personrefid by Projecteventcalendar.personrefid
    var person by PersonEntity referencedOn Projecteventcalendar.personrefid
}