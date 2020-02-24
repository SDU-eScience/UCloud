package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.share.api.ShareId
import dk.sdu.cloud.share.api.Shares
import dk.sdu.cloud.share.api.SharesByPath
import java.util.*

class ShareTesting (private val userA: UserAndClient, private val userB: UserAndClient) {
    private val folder = UUID.randomUUID().toString()
    private val shareFolderA = "folderToShare"
    private val shareFolderB = "anotherFolderToShare"

    suspend fun UserAndClient.runTest() {
        createFilesystem()
        with(userA) {
            val firstShareID = createShare(
                joinPath(homeFolder, folder, shareFolderA),
                userB.username,
                setOf(AccessRight.WRITE)
            )
            var listResponse = list()

            check(listResponse.itemsInTotal == 1)
            check(listResponse.items.first().path == joinPath(homeFolder, folder, shareFolderA))

            val secondShareID = createShare(
                joinPath(homeFolder, folder, shareFolderB),
                userB.username,
                setOf(AccessRight.READ)
            )
            listResponse = list()

            check(listResponse.itemsInTotal == 2)
            check(listResponse.items.first().path == joinPath(homeFolder, folder, shareFolderA))
            check(listResponse.items.last().path == joinPath(homeFolder, folder, shareFolderB))
        }
        with(userB) {
            val listResponse = list(false)
            check(listResponse.itemsInTotal == 2) { "Expected to only have 2 shares"}

            check(listResponse.items.first().path == joinPath(homeFolder, folder, shareFolderA)) {
                "First item is not ${joinPath(homeFolder, folder, shareFolderA)}"
            }
            check(listResponse.items.last().path == joinPath(homeFolder, folder, shareFolderB)){
                "Second item is not ${joinPath(homeFolder, folder, shareFolderB)}"
            }

            listResponse.items.forEach { share ->
                check(share.sharedBy == userA.username) { "Was not shared by ${userA.username}"}
            }


        }
    }

    private suspend fun UserAndClient.createFilesystem() {
        with(userA) {
            log.info("Creating fs for userA")
            createDir(folder)
            createDir(folder, shareFolderA)
            createDir(folder, shareFolderB)
        }
    }

    suspend fun UserAndClient.list(sharedByMe: Boolean = true): Page<SharesByPath> {
        return Shares.list.call(Shares.List.Request(sharedByMe), client).orThrow()
    }

    suspend fun UserAndClient.findByPath() {

    }

    private suspend fun UserAndClient.createShare(path: String, user: String, rights: Set<AccessRight>): ShareId {
        return Shares.create.call(Shares.Create.Request(user, path, rights), client).orThrow().id
    }

    suspend fun UserAndClient.update() {

    }

    suspend fun UserAndClient.revoke() {

    }

    suspend fun UserAndClient.accept() {

    }

    suspend fun UserAndClient.listFiles() {

    }

    companion object : Loggable {
        override val log = logger()
    }
}
