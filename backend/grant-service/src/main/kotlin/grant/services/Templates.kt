package dk.sdu.cloud.grant.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.grant.api.UploadTemplatesRequest
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode

object TemplateTable : SQLTable("templates") {
    val projectId = text("project_id", notNull = true)
    val personalProject = text("personal_project", notNull = false)
    val existingProject = text("existing_project", notNull = false)
    val newProject = text("new_project", notNull = false)
}

class TemplateService(
    private val projects: ProjectCache
) {
    suspend fun uploadTemplates(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        templates: UploadTemplatesRequest
    ) {
        ctx.withSession { session ->
            if (!projects.isAdminOfProject(projectId, actor)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                        setParameter("personalProject", templates.personalProject)
                        setParameter("existingProject", templates.existingProject)
                        setParameter("newProject", templates.newProject)
                    },

                    """
                        insert into templates (project_id, personal_project, existing_project, new_project) 
                        values (
                                :projectId,
                                :personalProject,
                                :existingProject,
                                :newProject
                            ) 
                        on conflict (project_id) do update set 
                            personal_project = excluded.personal_project,
                            existing_project = excluded.existing_project,
                            new_project = excluded.new_project
                    """
                )
        }
   }
}
