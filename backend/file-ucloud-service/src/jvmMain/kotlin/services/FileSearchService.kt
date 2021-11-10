package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.ucloud.api.*
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import org.slf4j.Logger

class FileSearchService(
    private val elasticQueryService: ElasticQueryService,
    private val db: DBContext
) {
    suspend fun search(request: AdvancedSearchRequest, actorAndProject: ActorAndProject):PageV2<ElasticIndexedFile> {
        if (request.extensions.isNullOrEmpty() && request.fileName.isNullOrBlank()) {
            log.debug("Empty search return empty page")
            return PageV2(0, emptyList(), null)
        }

        val includeShares = request.includeShares ?: false
        val roots = ArrayList<String>().apply {
            add("/home/${actorAndProject.actor.safeUsername()}")

            if (includeShares) {
                val sharesMounts = db.withSession { session ->
                    session.sendPreparedStatement(
                        {
                            setParameter("username", actorAndProject.actor.safeUsername())
                        },
                        """
                            select available_at
                            from file_orchestrator.shares
                            where shared_with = :username
                        """
                    ).rows.map { it.getString(0)?.substringAfterLast("/") }
                }
                addAll(sharesMounts.map {"/collections/${it}"})
            }

            //TODO() Note(Henrik): Does this still apply in new system or can project folders be located other places
            val projects = db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                    },
                    """
                        select id
                        from project.projects pr 
                            join project.project_members pm on pr.id = pm.project_id
                        where pm.username = :username
                    """
                ).rows.map { it.getString(0) }
            }

            addAll(projects.map { "/projects/${it}" })
        }

        return elasticQueryService.query(
            FileQuery(
                roots = roots,
                fileNameQuery = request.fileName?.let { listOf(it) },
                extensions = request.extensions?.takeIf { it.isNotEmpty() }?.let { exts ->
                    AnyOf.with(*exts.map {it.removePrefix(".") }.toTypedArray())
                },
                fileTypes = request.fileTypes?.takeIf { it.isNotEmpty() }
                    ?.let { AnyOf.with(*it.toTypedArray())}
            ),
            request.normalize()
        )
    }


    companion object : Loggable {
        override val log: Logger = logger()
    }
}
