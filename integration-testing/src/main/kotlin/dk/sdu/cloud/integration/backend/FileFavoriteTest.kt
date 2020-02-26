package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.CopyRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.MoveRequest
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteRequest
import dk.sdu.cloud.file.gateway.api.FileGatewayDescriptions
import dk.sdu.cloud.file.gateway.api.FileResource
import dk.sdu.cloud.file.gateway.api.ListAtDirectoryRequest
import dk.sdu.cloud.file.gateway.api.StatRequest
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.delay
import java.util.*

class FileFavoriteTest(private val userA: UserAndClient) {
    private val testId = UUID.randomUUID().toString()
    private val favoriteMe = "favorite-me"
    private val copyName = "copy"
    private val moveName = "move"

    private suspend fun UserAndClient.toggleFavorite(name: String) {
        FileFavoriteDescriptions.toggleFavorite.call(
            ToggleFavoriteRequest(
                joinPath(
                    homeFolder,
                    testId,
                    name
                )
            ),
            client
        ).orThrow()
    }

    private suspend fun UserAndClient.checkFavoriteStatus(name: String, shouldBeFavorite: Boolean) {
        retrySection(attempts = 10, delay = 2_000) {
            log.info("Checking if $name shouldBeFavorite=$shouldBeFavorite")
            val file = FileGatewayDescriptions.stat.call(
                StatRequest(joinPath(homeFolder, testId, name), null),
                client
            ).orThrow()

            require(file.favorited == shouldBeFavorite) {
                "File favorite status should be $shouldBeFavorite (stat)\nGot file: $file"
            }

            val list = FileGatewayDescriptions.listAtDirectory.call(
                ListAtDirectoryRequest(
                    joinPath(homeFolder, testId),
                    10,
                    0,
                    null,
                    null,
                    null,
                    setOf(FileResource.PATH, FileResource.FAVORITES)
                ),
                client
            ).orThrow()

            require(list.items.any { it.path.fileName() == name && it.favorited == shouldBeFavorite }) {
                "File favorite status should be $shouldBeFavorite (list)\nGot list: $list"
            }
        }
    }

    suspend fun runTest() {
        with(userA) {
            log.info("Running file-favorite test")
            createDir(testId)
            createDir(testId, favoriteMe)

            // Wait for file to be indexed
            delay(5000)

            log.info("Initial test")
            checkFavoriteStatus(favoriteMe, false)

            log.info("Toggle test")
            toggleFavorite(favoriteMe)
            checkFavoriteStatus(favoriteMe, true)

            log.info("Toggle off")
            toggleFavorite(favoriteMe)
            checkFavoriteStatus(favoriteMe, false)

            log.info("Repeated toggles")
            repeat(10) { toggleFavorite(favoriteMe) }
            checkFavoriteStatus(favoriteMe, false)

            log.info("Repeated toggles (2)")
            repeat(3) { toggleFavorite(favoriteMe) }
            checkFavoriteStatus(favoriteMe, true)

            log.info("Copy test (Test that favorite status is cleared)")
            FileDescriptions.copy.call(
                CopyRequest(
                    path = joinPath(
                        homeFolder,
                        testId,
                        favoriteMe
                    ),
                    newPath = joinPath(
                        homeFolder,
                        testId,
                        copyName
                    )
                ),
                client
            ).orThrow()
            checkFavoriteStatus(copyName, false)
            toggleFavorite(copyName)
            checkFavoriteStatus(copyName, true)

            log.info("Move test (Test that favorite status is preserved)")
            FileDescriptions.move.call(
                MoveRequest(
                    path = joinPath(
                        homeFolder,
                        testId,
                        copyName
                    ),
                    newPath = joinPath(
                        homeFolder,
                        testId,
                        moveName
                    )
                ),
                client
            ).orThrow()
            checkFavoriteStatus(moveName, true)
            toggleFavorite(moveName)
            checkFavoriteStatus(moveName, false)
            FileDescriptions.move.call(
                MoveRequest(
                    path = joinPath(
                        homeFolder,
                        testId,
                        moveName
                    ),
                    newPath = joinPath(
                        homeFolder,
                        testId,
                        copyName
                    )
                ),
                client
            ).orThrow()
            checkFavoriteStatus(copyName, false)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
