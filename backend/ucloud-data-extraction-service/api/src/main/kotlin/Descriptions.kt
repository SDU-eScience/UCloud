package dk.sdu.cloud.ucloud.data.extraction.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.bindEntireRequestFromBody

object UcloudDataExtractions : CallDescriptionContainer("ucloud.data.extraction") {
    val baseContext = "/api/ucloud/data/extraction"
}