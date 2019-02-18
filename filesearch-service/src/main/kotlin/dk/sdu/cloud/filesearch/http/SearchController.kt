package dk.sdu.cloud.filesearch.http

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.VerifyFileKnowledgeRequest
import dk.sdu.cloud.filesearch.api.FileSearchDescriptions
import dk.sdu.cloud.filesearch.api.SearchResult
import dk.sdu.cloud.filesearch.api.TimestampQuery
import dk.sdu.cloud.indexing.api.AllOf
import dk.sdu.cloud.indexing.api.AnyOf
import dk.sdu.cloud.indexing.api.Comparison
import dk.sdu.cloud.indexing.api.ComparisonOperator
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.PredicateCollection
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.QueryRequest
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page
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
    private val client: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileSearchDescriptions.simpleSearch) {
            val roots = rootsForUser(ctx.securityPrincipal.username)

            val queryResponse = QueryDescriptions.query.call(
                QueryRequest(
                    query = FileQuery(
                        roots = roots,
                        fileNameQuery = listOf(request.query),
                        owner = AllOf.with(ctx.securityPrincipal.username)
                    ),
                    itemsPerPage = request.itemsPerPage,
                    page = request.page
                ),
                client
            ).orThrow()

            ok(verify(queryResponse, ctx.securityPrincipal.username, client))
        }

        implement(FileSearchDescriptions.advancedSearch) {
            val roots = rootsForUser(ctx.securityPrincipal.username)
            val queryResponse = QueryDescriptions.query.call(
                QueryRequest(
                    query = FileQuery(
                        roots = roots,
                        owner = AllOf.with(ctx.securityPrincipal.username),

                        fileNameQuery = request.fileName?.let { listOf(it) },

                        extensions = request.annotations?.let { AnyOf.with(*it.toTypedArray()) },
                        fileTypes = request.fileTypes?.let { AnyOf.with(*it.toTypedArray()) },
                        annotations = request.annotations?.let { AnyOf.with(*it.toTypedArray()) },
                        sensitivity = request.sensitivity?.let { AnyOf.with(*it.toTypedArray()) },

                        createdAt = request.createdAt?.toPredicateCollection(),
                        modifiedAt = request.modifiedAt?.toPredicateCollection()
                    ),
                    itemsPerPage = request.itemsPerPage,
                    page = request.page
                ),
                client
            ).orThrow()

            ok(verify(queryResponse, ctx.securityPrincipal.username, client))
        }
    }

    private fun TimestampQuery.toPredicateCollection(): PredicateCollection<Comparison<Long>> {
        val predicates = ArrayList<Comparison<Long>>()
        before?.let { predicates.add(Comparison(it, ComparisonOperator.LESS_THAN_EQUALS)) }
        after?.let { predicates.add(Comparison(it, ComparisonOperator.GREATER_THAN_EQUALS)) }
        return AllOf(predicates.map { AnyOf(listOf(it)) })
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
                queryResponse.items.map { it.path }
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

        return Page(
            itemsInTotal = queryResponse.itemsInTotal,
            pageNumber = queryResponse.pageNumber,
            itemsPerPage = queryResponse.itemsPerPage,
            items = queryResultsVerified
        )
    }

    // TODO Get these from the storage-service
    private fun rootsForUser(user: String): List<String> = listOf("/home/$user")

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
