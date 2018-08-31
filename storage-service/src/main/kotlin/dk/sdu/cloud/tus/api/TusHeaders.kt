package dk.sdu.cloud.tus.api

object TusHeaders {
    /**
     * The Tus-Max-Size response header MUST be a non-negative integer indicating the maximum allowed size of an
     * entire upload in bytes. The Server SHOULD set this header if there is a known hard limit.
     */
    const val MaxSize = "Tus-Max-Size"

    /**
     * The Tus-Extension response header MUST be a comma-separated list of the extensions supported by the Server.
     * If no extensions are supported, the Tus-Extension header MUST be omitted.
     */
    const val Extension = "Tus-Extension"

    /**
     * The Upload-Offset request and response header indicates a byte offset within a resource. The value MUST be a
     * non-negative integer.
     */
    const val UploadOffset = "Upload-Offset"

    /**
     * The Upload-Length request and response header indicates the size of the entire upload in bytes. The value
     * MUST be a non-negative integer.
     */
    const val UploadLength = "Upload-Length"

    /**
     * The Tus-Resumable header MUST be included in every request and response except for OPTIONS requests.
     * The value MUST be the version of the protocol used by the Client or the Server.
     *
     * If the the version specified by the Client is not supported by the Server, it MUST respond with the 412
     * Precondition Failed status and MUST include the Tus-TUS_VERSION header into the response. In addition, the
     * Server MUST NOT process the request.
     */
    const val Resumable = "Tus-Resumable"

    /**
     * The Tus-TUS_VERSION response header MUST be a comma-separated list of protocol versions supported by the Server.
     * The list MUST be sorted by Server’s preference where the first one is the most preferred one.
     */
    const val Version = "Tus-Version"

    const val UploadMetadata = "Upload-Metadata"

    val KnownHeaders = listOf(
        MaxSize,
        Extension,
        UploadOffset,
        UploadLength,
        Resumable
    )
}

object TusExtensions {
    const val Creation = "Creation"
}

