package dk.sdu.cloud.filesearch.http

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.filesearch.api.FileSearchDescriptions
import dk.sdu.cloud.filesearch.api.SearchResult
import dk.sdu.cloud.indexing.api.AnyOf
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.QueryRequest
import dk.sdu.cloud.project.api.ListProjectsRequest
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.ScrollResult
import dk.sdu.cloud.share.api.Shares
import io.ktor.http.HttpStatusCode
import org.slf4j.Logger

/**
 * An exception that can be thrown when searching in files
 */
sealed class SearchException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    /**
     * For internal server errors. Likely to be external.
     */
    class InternalServerError : SearchException("Internal Server Error", HttpStatusCode.InternalServerError)
}

/**
 * A controller for [FileSearchDescriptions]
 */
class SearchController(
    private val serviceClient: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileSearchDescriptions.advancedSearch) {
            if (request.extensions.isNullOrEmpty() && request.fileName.isNullOrBlank()) {
                log.debug("Empty search return empty page")
                ok(Page(0, 0, 0, emptyList()))
                return@implement
            }

            val includeShares = request.includeShares ?: false
            val roots = ArrayList<String>().apply {
                add("/home/${ctx.securityPrincipal.username}")

                if (includeShares) {
                    val userClient = createUserClient(serviceClient, ctx as HttpCall)
                    val sharesRoots = Shares.list.call(Shares.List.Request(false, itemsPerPage = 100), userClient)
                        .orThrow().items.map { it.path }

                    addAll(sharesRoots)
                }

                val projects = ProjectMembers.userStatus.call(
                    UserStatusRequest(ctx.securityPrincipal.username),
                    serviceClient
                ).orNull()?.membership?.map { it.projectId } ?: emptyList()
                log.debug("Using the following projects: ${projects}")

                addAll(projects.map { "/projects/${it}" })
            }

            val queryResponse = QueryDescriptions.query.call(
                QueryRequest(
                    query = FileQuery(
                        roots = roots,
                        fileNameQuery = request.fileName?.let { listOf(it) },

                        extensions = request.extensions?.takeIf { it.isNotEmpty() }?.let { exts ->
                            AnyOf.with(*exts.map { it.removePrefix(".") }.toTypedArray())
                        },
                        fileTypes = request.fileTypes?.takeIf { it.isNotEmpty() }
                            ?.let { AnyOf.with(*it.toTypedArray()) }
                    ),
                    itemsPerPage = request.itemsPerPage,
                    page = request.page
                ),
                serviceClient
            ).orThrow()

            ok(verify(queryResponse, ctx.securityPrincipal.username, serviceClient))
        }
    }

    // TODO Move to service
    private suspend fun verify(
        queryResponse: Page<StorageFile>,
        user: String,
        cloud: AuthenticatedClient
    ): Page<SearchResult> {
        val verifiedFiles = FileDescriptions.verifyFileKnowledge.call(
            VerifyFileKnowledgeRequest(
                user,
                queryResponse.items.map { it.path },
                KnowledgeMode.Permission(false)
            ),
            cloud
        ).orRethrowAs { throw SearchException.InternalServerError() }

        val queryResultsVerified = ArrayList<SearchResult>()

        if (verifiedFiles.responses.size != queryResponse.items.size) {
            log.warn("verifiedFiles.size != queryResponse.size")
            throw SearchException.InternalServerError()
        }

        for ((index, verified) in verifiedFiles.responses.withIndex()) {
            if (verified) queryResultsVerified.add(queryResponse.items[index])
        }

        if (verifiedFiles.responses.size != queryResultsVerified.size) {
            log.debug("Returning ${queryResultsVerified.size} out of ${verifiedFiles.responses.size}")
        }

        return Page(
            items = queryResultsVerified,
            itemsPerPage = queryResponse.itemsPerPage,
            pageNumber = queryResponse.pageNumber,
            itemsInTotal = queryResponse.itemsInTotal
        )
    }

    private fun createUserClient(cloud: AuthenticatedClient, ctx: HttpCall): AuthenticatedClient {
        val bearer = ctx.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        return cloud.withoutAuthentication().bearerAuth(bearer)
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
