package dk.sdu.cloud.file.services.background

import dk.sdu.cloud.defaultMapper
import io.ktor.http.HttpStatusCode

data class BackgroundRequest(
    val jobId: String,
    val requestType: String,
    val requestMessage: String
)

data class BackgroundResponse(
    val responseCode: Int,
    val response: String
) {
    constructor(responseCode: HttpStatusCode, response: String) : this(responseCode.value, response)
    constructor(responseCode: HttpStatusCode, response: Any) : this(
        responseCode.value,
        defaultMapper.writeValueAsString(response)
    )
}

data class BackgroundJob(
    val request: BackgroundRequest,
    val response: BackgroundResponse?
)

interface BackgroundJobDao<Session> {
    fun findOrNull(session: Session, jobId: String): BackgroundJob?
    fun create(session: Session, request: BackgroundRequest)
    fun setResponse(session: Session, jobId: String, response: BackgroundResponse)
}
