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
data class ClearRequest(val trashPath: String? = null)

object FileTrashDescriptions : CallDescriptionContainer("files.trash") {
    val baseContext = "/api/files/trash"

    val trash = call<TrashRequest, Unit, CommonErrorMessage>("trash") {
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

    val clear = call<ClearRequest, Unit, CommonErrorMessage>("clear") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"clear"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
