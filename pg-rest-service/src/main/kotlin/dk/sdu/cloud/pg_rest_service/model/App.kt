package dk.sdu.cloud.pg_rest_service.model
import org.jetbrains.exposed.dao.IntIdTable

object App : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val created_ts = datetime("created_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val app_name = text("app_name").nullable()
	val app_description_text  = text("app_description_text").nullable()
	val active = Project.integer("active").nullable()
	val prepped = Project.integer("prepped").nullable()
	val git_url  = text("git_url").nullable()
	val cwl_file  = blob("cwl_file").nullable()
	val principal_ref_id = reference("principal", Principal).nullable()
}



