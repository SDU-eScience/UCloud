package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Irodsresourcetype : IntIdTable() {
    val irodsresourcetypename = text("irodsresourcetypename").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val irodsresourcetypeidmap = integer("irodsresourcetypeidmap").nullable()
}
class IrodsresourcetypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<IrodsresourcetypeEntity>(Irodsresourcetype)

    var irodsresourcetypename by Irodsresourcetype.irodsresourcetypename
    var created_ts by Irodsresourcetype.created_ts
    var modified_ts by Irodsresourcetype.modified_ts
    var active by Irodsresourcetype.active
    var irodsresourcetypeidmap by Irodsresourcetype.irodsresourcetypeidmap
}