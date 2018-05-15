package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Project_role : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val projectrolename = text("projectrolename").nullable()
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
}

