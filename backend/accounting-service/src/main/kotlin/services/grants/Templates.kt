package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.Configuration
import dk.sdu.cloud.accounting.api.grants.RetrieveTemplatesResponse
import dk.sdu.cloud.accounting.api.grants.Templates
import dk.sdu.cloud.accounting.api.grants.UploadTemplatesRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.grant.api.GrantApplication
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*

class GrantTemplateService(
    private val db: DBContext,
    private val config: Configuration
) {
    suspend fun uploadTemplates(
        actorAndProject: ActorAndProject,
        template: UploadTemplatesRequest
    ) {
        db.withSession(remapExceptions = true) { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("projectId", actorAndProject.project)
                    when (template) {
                        is Templates.PlainText -> {
                            setParameter("personalProject", template.personalProject)
                            setParameter("existingProject", template.existingProject)
                            setParameter("newProject", template.newProject)
                        }
                        else -> {
                            throw RPCException.fromStatusCode(
                                HttpStatusCode.BadRequest,
                                "Missing expected form format"
                            )
                        }
                    }
                },

                """
                    insert into "grant".templates (project_id, personal_project, existing_project, new_project) 
                    select :projectId, :personalProject, :existingProject, :newProject
                    from project.project_members pm
                    where
                        pm.username = :username and
                        pm.project_id = :projectId and
                        (pm.role = 'ADMIN' or pm.role = 'PI')
                    on conflict (project_id) do update set 
                        personal_project = excluded.personal_project,
                        existing_project = excluded.existing_project,
                        new_project = excluded.new_project
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException(
                    "Unable to upload templates. Do you have the correct permissions?",
                    HttpStatusCode.BadRequest
                )
            }
        }

    }

    suspend fun fetchTemplates(
       actorAndProject: ActorAndProject,
       projectId: String
    ): RetrieveTemplatesResponse {
        return db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("active_project", actorAndProject.project)
                    setParameter("project_id", projectId)
                },
                """
                    select
                        t.personal_project,
                        t.new_project,
                        t.existing_project
                    from
                        "grant".templates t left join
                        project.project_members pm on
                            pm.username = :username and
                            pm.project_id = :project_id and
                            (pm.role = 'PI' or pm.role = 'ADMIN')
                    where
                        t.project_id = :project_id
                        and (
                            :username = '_ucloud'
                            or (
                                pm.username is not null or 
                                "grant".can_submit_application(
                                    :username,
                                    :project_id,
                                    case
                                        when :active_project::text is null then :username
                                        else :active_project::text
                                    end,
                                    case
                                        when :active_project::text is null then 'personal'
                                        else 'existing_project'
                                    end
                                )
                            )
                        )
                """
            ).rows.map {
                Templates.PlainText(it.getString(0)!!, it.getString(1)!!, it.getString(2)!!)
            }.singleOrNull()
                ?:
                    Templates.PlainText(config.defaultTemplate, config.defaultTemplate, config.defaultTemplate)
        }
    }
}
