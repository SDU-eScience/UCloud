package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Irodsruleexectype : IntIdTable() {
    val lastmodified = datetime("lastmodified")
    val irodsruleexectypetext = text("irodsruleexectypetext").nullable()
    val irodsruleexectypeidmap = integer("irodsruleexectypeidmap").nullable()
    val active = integer("active").nullable()
}
class IrodsruleexectypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<IrodsruleexectypeEntity>(Irodsruleexectype)

    var lastmodified by Irodsruleexectype.lastmodified
    var irodsruleexectypetext by Irodsruleexectype.irodsruleexectypetext
    var irodsruleexectypeidmap by Irodsruleexectype.irodsruleexectypeidmap
    var active by Irodsruleexectype.active
}