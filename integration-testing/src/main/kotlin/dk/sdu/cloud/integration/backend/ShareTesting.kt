package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.share.api.Share
import dk.sdu.cloud.share.api.ShareId
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.api.Shares
import dk.sdu.cloud.share.api.SharesByPath
import io.ktor.http.HttpStatusCode
import java.util.*

class ShareTesting (private val userA: UserAndClient, private val userB: UserAndClient) {
    private val folder = UUID.randomUUID().toString()
    private val shareFolderA = "folderToShare"
    private val shareFolderB = "anotherFolderToShare"

    suspend fun UserAndClient.runTest() {
        createFilesystem()
        var firstShareId: ShareId
        var secondShareId: ShareId
        with(userA) {
            firstShareId = createShare(
                joinPath(homeFolder, folder, shareFolderA),
                userB.username,
                setOf(AccessRight.WRITE)
            )
            var listResponse = list()

            check(listResponse.itemsInTotal == 1)
            check(listResponse.items.first().path == joinPath(homeFolder, folder, shareFolderA))

            secondShareId = createShare(
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
            var listResponse = list(false)
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

            check(listResponse.items.first().shares.first().state == ShareState.REQUEST_SENT)
            check(listResponse.items.last().shares.first().state == ShareState.REQUEST_SENT)

            accept(firstShareId)

            revoke(secondShareId)

            listResponse = list(false)

            check(listResponse.itemsInTotal == 1)
            check(listResponse.items.first().path == joinPath(homeFolder, folder, shareFolderA))
            check(listResponse.items.first().shares.first().state == ShareState.ACCEPTED ||
                    listResponse.items.first().shares.first().state == ShareState.UPDATING)
            check(listResponse.items.first().shares.first().rights.contains(AccessRight.WRITE))
        }

        with(userA) {
            update(firstShareId, setOf(AccessRight.READ))
            val listResponse = list()
            check(listResponse.itemsInTotal == 1)
            check(listResponse.items.first().shares.first().rights.contains(AccessRight.READ))
        }

        with(userB) {
            val sharesByPath = findByPath(joinPath(homeFolder, folder, shareFolderA))
            check(sharesByPath.sharedBy == userA.username)

            try {
                findByPath("not/path")
            } catch (ex: RPCException) {
                check(ex.httpStatusCode == HttpStatusCode.NotFound)
            }

            val listedFiles = listFiles()
            check(listedFiles.itemsInTotal == 1)
            check(listedFiles.items.first().path == joinPath(homeFolder, folder, shareFolderA))
        }

        with(userA) {
            revoke(firstShareId)
            val listResponse = list()
            check(listResponse.itemsInTotal == 0)
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

    private suspend fun UserAndClient.list(sharedByMe: Boolean = true): Page<SharesByPath> {
        return Shares.list.call(Shares.List.Request(sharedByMe), client).orThrow()
    }

    private suspend fun UserAndClient.findByPath(path: String): SharesByPath {
        return Shares.findByPath.call(Shares.FindByPath.Request(path), client).orThrow()
    }

    private suspend fun UserAndClient.createShare(path: String, user: String, rights: Set<AccessRight>): ShareId {
        return Shares.create.call(Shares.Create.Request(user, path, rights), client).orThrow().id
    }

    private suspend fun UserAndClient.update(id: ShareId, rights: Set<AccessRight>) {
        Shares.update.call(Shares.Update.Request(id, rights), client).orThrow()
    }

    private suspend fun UserAndClient.revoke(id: ShareId) {
        Shares.revoke.call(FindByLongId(id), client).orThrow()
    }

    private suspend fun UserAndClient.accept(id: ShareId) {
        Shares.accept.call(Shares.Accept.Request(id, null), client).orThrow()
    }

    private suspend fun UserAndClient.listFiles(): Page<StorageFile> {
        return Shares.listFiles.call(Shares.ListFiles.Request(25, 0), client).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
