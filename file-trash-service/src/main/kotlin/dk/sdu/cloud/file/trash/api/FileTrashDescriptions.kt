package dk.sdu.cloud.file.trash.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class TrashRequest(val files: List<String>)
data class TrashResponse(val failures: List<String>)

object FileTrashDescriptions : CallDescriptionContainer("files.trash") {
    val baseContext = "/api/files/trash"

    val trash = call<TrashRequest, TrashResponse, CommonErrorMessage>("trash") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val clear = call<Unit, Unit, CommonErrorMessage>("clear") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"clear"
            }
        }
    }
}
