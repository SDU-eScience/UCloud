package dk.sdu.cloud.file.trash.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
data class TrashRequest(val files: List<String>)

@Serializable
data class ClearRequest(val trashPath: String? = null)

@TSTopLevel
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
