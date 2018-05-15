package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable

object Project_org_relation : IntIdTable() {
	val projectrefid = Principal_systemrole_relation.reference("project", Project)
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
	val orgrefid = Project_org_relation.reference("org", Org)
}
