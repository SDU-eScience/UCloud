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
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.api.Shares
import dk.sdu.cloud.share.api.SharesByPath
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import java.util.*

class ShareTesting (private val userA: UserAndClient, private val userB: UserAndClient) {
    suspend fun runTest() {

    }
    /*
    private val folder = UUID.randomUUID().toString()
    private val shareFolderA = "folderToShare"
    private val shareFolderB = "anotherFolderToShare"

    suspend fun runTest() {
        log.info("Share tests are starting")
        with(userA) {
            createFilesystem()
        }
        var firstShareId: ShareId
        var secondShareId: ShareId

        with(userA) {
            //Clean up shares from last run
            var listResponse = list()

            listResponse.items.forEach {
                revoke(it.shares.first().id)
            }
            //Done cleanup
            firstShareId = createShare(
                joinPath(homeFolder, folder, shareFolderA),
                userB.username,
                setOf(AccessRight.WRITE)
            )
            delay(1000)
            listResponse = list()

            require(listResponse.itemsInTotal == 1) {"Number of shares was not 1 but is: ${listResponse.itemsInTotal}"}
            check(listResponse.items.single().path == joinPath(homeFolder, folder, shareFolderA)) {
                "Only item is not ${joinPath(homeFolder, folder, shareFolderA)}"
            }

            secondShareId = createShare(
                joinPath(homeFolder, folder, shareFolderB),
                userB.username,
                setOf(AccessRight.READ)
            )
            delay(500)

            listResponse = list()

            require(listResponse.itemsInTotal == 2) {"Number of shares was not 2 but is: ${listResponse.itemsInTotal}"}
            check(listResponse.items.first().path == joinPath(homeFolder, folder, shareFolderA)) {
                "First item is not ${joinPath(homeFolder, folder, shareFolderA)}"
            }
            check(listResponse.items.last().path == joinPath(homeFolder, folder, shareFolderB)) {
                "Second item is not ${joinPath(homeFolder, folder, shareFolderB)}"
            }

        }
        with(userB) {
            var listResponse = list(false)
            require(listResponse.itemsInTotal == 2) { "Expected to only have 2 shares"}

            check(listResponse.items.first().path == joinPath(userA.homeFolder, folder, shareFolderA)) {
                "First item is not ${joinPath(userA.homeFolder, folder, shareFolderA)}"
            }
            check(listResponse.items.last().path == joinPath(userA.homeFolder, folder, shareFolderB)){
                "Second item is not ${joinPath(userA.homeFolder, folder, shareFolderB)}"
            }

            listResponse.items.forEach { share ->
                check(share.sharedBy == userA.username) { "Was not shared by ${userA.username}"}
            }

            check(listResponse.items.first().shares.first().state == ShareState.REQUEST_SENT) {
                "State was not REQUEST_SENT but ${listResponse.items.first().shares.first().state}"
            }
            check(listResponse.items.last().shares.first().state == ShareState.REQUEST_SENT) {
                "State was not REQUEST_SENT but ${listResponse.items.last().shares.first().state}"
            }

            accept(firstShareId)

            revoke(secondShareId)

            //Give chance to update states
            delay(1000)

            listResponse = list(false)

            require(listResponse.itemsInTotal == 1) {"Number of items is not 1 but ${listResponse.itemsInTotal}"}
            check(listResponse.items.single().path == joinPath(userA.homeFolder, folder, shareFolderA)) {
                "Path does not match: ${joinPath(userA.homeFolder, folder, shareFolderA)}"
            }
            check(listResponse.items.single().shares.first().state == ShareState.ACCEPTED ||
                    listResponse.items.single().shares.first().state == ShareState.UPDATING) {
                "Item is not in Accepted or Updating state. State is: ${listResponse.items.single().shares.first().state}"
            }
            check(listResponse.items.single().shares.first().rights.contains(AccessRight.WRITE)) {
                "Rights are not WRITE but: ${listResponse.items.single().shares.first().rights}"
            }
        }

        with(userA) {
            update(firstShareId, setOf(AccessRight.READ))
            val listResponse = list()
            require(listResponse.itemsInTotal == 1)
            check(listResponse.items.single().shares.first().rights.contains(AccessRight.READ))
        }

        with(userB) {
            val sharesByPath = findByPath(joinPath(userA.homeFolder, folder, shareFolderA))
            check(sharesByPath.sharedBy == userA.username) {"FindbyPath error: Shareby is not ${userA.username}"}

            try {
                findByPath(joinPath(homeFolder, folder, "notThere"))
            } catch (ex: RPCException) {
                check(ex.httpStatusCode == HttpStatusCode.NotFound) {
                    "status code is not 400 but: ${ex.httpStatusCode}"
                }
            }

            val listedFiles = listFiles()
            require(listedFiles.itemsInTotal == 1) {
                "Number of shares after update is not as expected. Is ${listedFiles.itemsInTotal} but should be 1"
            }
            check(listedFiles.items.single().path == joinPath(userA.homeFolder, folder, shareFolderA)) {
                "Path ${listedFiles.items.single().path} is not correct. " +
                        "Should be ${ joinPath(userA.homeFolder, folder, shareFolderA)}"
            }
        }

        with(userA) {
            revoke(firstShareId)
            delay(1000)
            val listResponse = list()
            require(listResponse.itemsInTotal == 0) {
                "After double revoke there should be no more, but there are ${listResponse.itemsInTotal} left"
            }
        }
        log.info("Share tests are DONE")
    }

    private suspend fun UserAndClient.createFilesystem() {
        with(userA) {
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
     */
}
