package dk.sdu.cloud.storage.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.storage.model.StorageFile

data class FindByPath(val path: String)

object FileDescriptions : RESTDescriptions(StorageServiceDescription) {
    private val baseContext = "/api/files"

    val listAtPath = callDescription<FindByPath, List<StorageFile>, CommonErrorMessage> {
        prettyName = "filesListAtPath"
        path { using(baseContext) }
        params {
            +boundTo(FindByPath::path)
        }
    }
}