package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityFilter
import dk.sdu.cloud.activity.api.ActivityForFrontend
import dk.sdu.cloud.activity.api.type
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.KnowledgeMode
import dk.sdu.cloud.file.api.VerifyFileKnowledgeRequest
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.project.repository.api.ProjectRepository
import dk.sdu.cloud.project.repository.api.RepositoryListRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.NormalizedScrollRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.ScrollResult
import kotlinx.coroutines.runBlocking

class ActivityService(
    private val activityEventElasticDao: ActivityEventElasticDao,
    private val fileLookupService: FileLookupService,
    private val client: AuthenticatedClient
) {
    suspend fun findEventsForPath(
        pagination: NormalizedPaginationRequest,
        path: String,
        userAccessToken: String,
        user: String,
        causedBy: String? = null
    ): Page<ActivityForFrontend> {
        val fileStat = fileLookupService.lookupFile(path, userAccessToken, user, causedBy)
        return activityEventElasticDao.findByFilePath(pagination, fileStat.path)
    }

    suspend fun browseActivity(
        scroll: NormalizedScrollRequest<Int>,
        user: String,
        userFilter: ActivityFilter? = null,
        projectID: String? = null
    ): ScrollResult<ActivityForFrontend, Int> {
        var filter = ActivityEventFilter(
            offset = scroll.offset,
            user = userFilter?.user,
            minTimestamp = userFilter?.minTimestamp,
            maxTimestamp = userFilter?.maxTimestamp,
            type = userFilter?.type
        )

        val allEvents = if (!projectID.isNullOrBlank()) {
            val repos = ProjectRepository.list.call(
                RepositoryListRequest(
                    user,
                    null,
                    null
                ),
                client.withProject(projectID)
            ).orThrow().items

            val withKnowledge = FileDescriptions.verifyFileKnowledge.call(
                VerifyFileKnowledgeRequest(
                    user,
                    repos.map { "/projects/${projectID}/${it.name}" },
                    KnowledgeMode.Permission(requireWrite = false)
                ),
                client
            ).orThrow().responses

            val filteredRepos = repos.filterIndexed { index, _ -> withKnowledge[index] }

            activityEventElasticDao.findProjectEvents(
                scroll.scrollSize,
                filter,
                projectID,
                filteredRepos
            )

        } else {
            if (filter.user == null) {
                filter = filter.copy(user = user)
            }
            activityEventElasticDao.findUserEvents(
                scroll.scrollSize,
                filter
            )
        }
        val results = allEvents.map { ActivityForFrontend(it.type, it.timestamp, it) }

        val nextOffset = allEvents.size + (scroll.offset ?: 0)

        return ScrollResult(results, nextOffset, endOfScroll = allEvents.size < scroll.scrollSize)
    }


    companion object : Loggable {
        override val log = logger()
    }
}

