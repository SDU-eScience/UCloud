package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.indexing.api.FileSearchDescriptions
import dk.sdu.cloud.indexing.api.SearchResult
import dk.sdu.cloud.indexing.services.IndexQueryService
import dk.sdu.cloud.indexing.services.toExternalResult
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.VerifyFileKnowledgeRequest
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.Logger

class SearchController(
    private val indexQueryService: IndexQueryService
) : Controller {
    override val baseContext: String = FileSearchDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileSearchDescriptions.simpleSearch) { req ->
            logEntry(log, req)
            if (!protect()) return@implement

            val roots = listOf("/home/${call.request.currentUsername}") // TODO Get these from the storage-service
            val queryResponse = indexQueryService.simpleQuery(roots, req.query, req.normalize())
            val verifiedFiles = FileDescriptions.verifyFileKnowledge.call(
                VerifyFileKnowledgeRequest(
                    call.request.currentUsername,
                    queryResponse.items.map { it.path }
                ),
                call.cloudClient
            )

            if (verifiedFiles !is RESTResponse.Ok) {
                error(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
                return@implement
            }

            val queryResultsVerified = ArrayList<SearchResult>()

            if (verifiedFiles.result.responses.size != queryResponse.items.size) {
                log.warn("verifiedFiles.size != queryResponse.size")
                error(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
                return@implement
            }

            for ((index, verified) in verifiedFiles.result.responses.withIndex()) {
                if (verified) queryResultsVerified.add(queryResponse.items[index].toExternalResult())
            }

            ok(
                Page(
                    itemsInTotal = queryResponse.itemsInTotal,
                    pageNumber = queryResponse.pageNumber,
                    itemsPerPage = queryResponse.itemsPerPage,
                    items = queryResultsVerified
                )
            )
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}