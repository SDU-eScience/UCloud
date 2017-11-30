package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Irodsfileextension : IntIdTable() {
    val irodsfileextensiontext = text("irodsfileextensiontext").nullable()
    val lastmodified = datetime("lastmodified")
    val active = integer("active").nullable()
    val irodsfileextensionmapid = integer("irodsfileextensionmapid").nullable()
    val irodsfileextensiondesc = text("irodsfileextensiondesc").nullable()
}
class IrodsfileextensionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<IrodsfileextensionEntity>(Irodsfileextension)

    var irodsfileextensiontext by Irodsfileextension.irodsfileextensiontext
    var lastmodified by Irodsfileextension.lastmodified
    var active by Irodsfileextension.active
    var irodsfileextensionmapid by Irodsfileextension.irodsfileextensionmapid
    var irodsfileextensiondesc by Irodsfileextension.irodsfileextensiondesc
}