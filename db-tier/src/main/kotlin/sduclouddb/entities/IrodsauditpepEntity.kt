package sduclouddb.entities

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable


object Irodsauditpep : IntIdTable() {
    val phase = text("phase").nullable()
    val created_ts = datetime("created_ts")
    val modified_ts = datetime("modified_ts")
    val irodsauditpepname = text("irodsauditpepname").nullable()
    val active = integer("active").nullable()
    val type = text("type").nullable()
}

class IrodsauditpepEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object: IntEntityClass<IrodsauditpepEntity>(Irodsauditpep)

    var phase by Irodsauditpep.phase
    var created_ts by Irodsauditpep.created_ts
    var modified_ts by Irodsauditpep.modified_ts
    var irodsauditpepname by Irodsauditpep.irodsauditpepname
    var active by Irodsauditpep.active
    var type by Irodsauditpep.type
}
