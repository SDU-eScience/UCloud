package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Subsystemcommandcategory : IntIdTable() {
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val subsystemcommandcategorytext = text("subsystemcommandcategorytext").nullable()
}
class SubsystemcommandcategoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<SubsystemcommandcategoryEntity>(Subsystemcommandcategory)

    var created_ts by Subsystemcommandcategory.created_ts
    var modified_ts by Subsystemcommandcategory.modified_ts
    var subsystemcommandcategorytext by Subsystemcommandcategory.subsystemcommandcategorytext
}
