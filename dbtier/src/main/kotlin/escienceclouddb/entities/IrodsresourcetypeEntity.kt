package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Irodsresourcetype : IntIdTable() {
    val irodsresourcetypetext = text("irodsresourcetypetext").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val irodsresourcetypeidmap = integer("irodsresourcetypeidmap").nullable()
}
class IrodsresourcetypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<IrodsresourcetypeEntity>(Irodsresourcetype)

    var irodsresourcetypetext by Irodsresourcetype.irodsresourcetypetext
    var lastmodified by Irodsresourcetype.lastmodified
    var active by Irodsresourcetype.active
    var irodsresourcetypeidmap by Irodsresourcetype.irodsresourcetypeidmap
}