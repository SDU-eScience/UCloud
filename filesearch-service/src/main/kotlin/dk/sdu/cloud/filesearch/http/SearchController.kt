package dk.sdu.cloud.filesearch.http

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.file.api.FileDescriptions
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
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.cloudClient
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
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
class SearchController : Controller {
    override val baseContext: String = FileSearchDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileSearchDescriptions.simpleSearch) { req ->
            val roots = rootsForUser(call.securityPrincipal.username)

            val queryResponse = QueryDescriptions.query.call(
                QueryRequest(
                    FileQuery(
                        roots = roots,
                        fileNameQuery = listOf(req.query),
                        owner = AllOf.with(call.securityPrincipal.username)
                    ),
                    req.itemsPerPage,
                    req.page
                ),
                call.cloudClient
            ).orThrow()

            ok(verify(queryResponse, call.securityPrincipal.username, call.cloudClient))
        }

        implement(FileSearchDescriptions.advancedSearch) { req ->
            val roots = rootsForUser(call.securityPrincipal.username)
            val queryResponse = QueryDescriptions.query.call(
                QueryRequest(
                    FileQuery(
                        roots = roots,
                        owner = AllOf.with(call.securityPrincipal.username),

                        fileNameQuery = req.fileName?.let { listOf(it) },

                        extensions = req.annotations?.let { AnyOf.with(*it.toTypedArray()) },
                        fileTypes = req.fileTypes?.let { AnyOf.with(*it.toTypedArray()) },
                        annotations = req.annotations?.let { AnyOf.with(*it.toTypedArray()) },
                        sensitivity = req.sensitivity?.let { AnyOf.with(*it.toTypedArray()) },

                        createdAt = req.createdAt?.toPredicateCollection(),
                        modifiedAt = req.modifiedAt?.toPredicateCollection()
                    )
                ),
                call.cloudClient
            ).orThrow()

            ok(verify(queryResponse, call.securityPrincipal.username, call.cloudClient))
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
        queryResponse: Page<EventMaterializedStorageFile>,
        user: String,
        cloud: AuthenticatedCloud
    ): Page<SearchResult> {
        val verifiedFiles = FileDescriptions.verifyFileKnowledge.call(
            VerifyFileKnowledgeRequest(
                user,
                queryResponse.items.map { it.path }
            ),
            cloud
        ) as? RESTResponse.Ok ?: throw SearchException.InternalServerError()

        val queryResultsVerified = ArrayList<SearchResult>()

        if (verifiedFiles.result.responses.size != queryResponse.items.size) {
            log.warn("verifiedFiles.size != queryResponse.size")
            throw SearchException.InternalServerError()
        }

        for ((index, verified) in verifiedFiles.result.responses.withIndex()) {
            if (verified) queryResultsVerified.add(queryResponse.items[index].toExternalResult())
        }

        return Page(
            itemsInTotal = queryResponse.itemsInTotal,
            pageNumber = queryResponse.pageNumber,
            itemsPerPage = queryResponse.itemsPerPage,
            items = queryResultsVerified
        )
    }

    private fun EventMaterializedStorageFile.toExternalResult(): SearchResult = SearchResult(path, fileType)

    // TODO Get these from the storage-service
    private fun rootsForUser(user: String): List<String> = listOf("/home/$user")

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
