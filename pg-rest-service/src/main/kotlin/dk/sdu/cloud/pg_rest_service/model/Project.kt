package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable


object Project : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val created_ts = datetime("created_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val projectname = text("projectname").nullable()
	val projectstart = datetime("projectstart")
	val projectend= datetime("projectend")
	val projectshortname = text("projectshortname").nullable()
	val servername = text("servername").nullable()
	val projecttyperefid = Project.reference("project_type", Project_type)
	val active = integer("active").nullable()
	val visible = integer("visible").nullable()
}



