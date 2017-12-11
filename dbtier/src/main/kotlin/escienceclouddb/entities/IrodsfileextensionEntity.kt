package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Irodsfileextension : IntIdTable() {
    val irodsfileextensiontext = text("irodsfileextensiontext").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val active = integer("active").nullable()
    val irodsfileextensionmapid = integer("irodsfileextensionmapid").nullable()
    val irodsfileextensiondesc = text("irodsfileextensiondesc").nullable()
}
class IrodsfileextensionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<IrodsfileextensionEntity>(Irodsfileextension)

    var irodsfileextensiontext by Irodsfileextension.irodsfileextensiontext
    var created_ts by Irodsfileextension.created_ts
    var modified_ts by Irodsfileextension.modified_ts
    var active by Irodsfileextension.active
    var irodsfileextensionmapid by Irodsfileextension.irodsfileextensionmapid
    var irodsfileextensiondesc by Irodsfileextension.irodsfileextensiondesc
}