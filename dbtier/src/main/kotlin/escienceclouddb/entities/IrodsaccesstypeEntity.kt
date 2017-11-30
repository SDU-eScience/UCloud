import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Irodsaccesstype : IntIdTable() {
	val lastmodified = datetime("lastmodified")
	val irodsaccesstypetext = text("irodsaccesstypetext").nullable()
	val active = integer("irodsaccesstypeactive").nullable()
	val irodsaccesstypeidmap = integer("irodsaccesstypeidmap").nullable()
}

class IrodsaccesstypeEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<IrodsaccesstypeEntity>(Irodsaccesstype)

	var lastmodified by Irodsaccesstype.lastmodified
	var irodsaccesstypetext by Irodsaccesstype.irodsaccesstypetext
	var irodsaccesstypeactive by Irodsaccesstype.active
	var irodsaccesstypeidmap by Irodsaccesstype.irodsaccesstypeidmap
}