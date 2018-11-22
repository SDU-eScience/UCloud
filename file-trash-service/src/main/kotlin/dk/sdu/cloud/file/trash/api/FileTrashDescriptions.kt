package dk.sdu.cloud.file.trash.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.api.joinPath
import io.ktor.http.HttpMethod

data class TrashRequest(val files: List<String>)
data class TrashResponse(val failures: List<String>)

fun trashDirectory(username: String): String = joinPath(homeDirectory(username), "Trash")

object FileTrashDescriptions : RESTDescriptions("files.trash") {
    val baseContext = "/api/files/trash"

    val trash = callDescription<TrashRequest, TrashResponse, CommonErrorMessage> {
        name = "trash"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }

    val clear = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "clear"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"clear"
        }
    }
}
