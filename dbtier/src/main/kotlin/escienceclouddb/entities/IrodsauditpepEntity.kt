package escienceclouddb

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.transactions.transaction


object Irodsauditpep : IntIdTable() {
    val phase = text("phase").nullable()
    val lastmodified = datetime("lastmodified")
    val parm = text("parm").nullable()
    val active = integer("active").nullable()
    val type = text("type").nullable()
}

class IrodsauditpepEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<IrodsauditpepEntity>(Irodsauditpep)

    var phase by Irodsauditpep.phase
    var lastmodified by Irodsauditpep.lastmodified
    var parm by Irodsauditpep.parm
    var active by Irodsauditpep.active
    var type by Irodsauditpep.type
}
