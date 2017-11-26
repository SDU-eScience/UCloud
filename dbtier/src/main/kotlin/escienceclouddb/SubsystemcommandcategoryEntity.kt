package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Subsystemcommandcategory : IntIdTable() {
    val lastmodified = datetime("lastmodified")
    val subsystemcommandcategorytext = text("subsystemcommandcategorytext").nullable()
}
class SubsystemcommandcategoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SubsystemcommandcategoryEntity>(Subsystemcommandcategory)

    var lastmodified by Subsystemcommandcategory.lastmodified
    var subsystemcommandcategorytext by Subsystemcommandcategory.subsystemcommandcategorytext
}
