package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.filesearch.api.FileSearchDescriptions
import dk.sdu.cloud.filesearch.api.SearchResult
import dk.sdu.cloud.indexing.services.IndexQueryService
import dk.sdu.cloud.indexing.services.toExternalResult
import dk.sdu.cloud.service.*
import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.VerifyFileKnowledgeRequest
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.Logger

sealed class SearchException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    class InternalServerError : SearchException("Internal Server Error", HttpStatusCode.InternalServerError)
}

class SearchController(
    private val indexQueryService: IndexQueryService
) : Controller {
    override val baseContext: String = FileSearchDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileSearchDescriptions.simpleSearch) { req ->
            logEntry(log, req)
            if (!protect()) return@implement

            val roots = rootsForUser(call.request.currentUsername)
            val queryResponse = indexQueryService.simpleQuery(roots, req.query, req.normalize())
            ok(verify(queryResponse, call.request.currentUsername, call.cloudClient))
        }

        implement(FileSearchDescriptions.advancedSearch) { req ->
            logEntry(log, req)
            if (!protect()) return@implement

            val roots = rootsForUser(call.request.currentUsername)
            val queryResponse = indexQueryService.advancedQuery(
                roots,
                name = req.fileName,
                annotations = req.annotations,
                owner = null,
                modifiedAt = req.modifiedAt,
                createdAt = req.createdAt,
                extensions = req.extensions,
                fileTypes = req.fileTypes,
                paging = req.normalize(),
                sensitivity = req.sensitivity
            )

            ok(verify(queryResponse, call.request.currentUsername, call.cloudClient))
        }
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

    // TODO Get these from the storage-service
    private fun rootsForUser(user: String): List<String> = listOf("/home/$user")

    companion object : Loggable {
        override val log: Logger = logger()
    }
}