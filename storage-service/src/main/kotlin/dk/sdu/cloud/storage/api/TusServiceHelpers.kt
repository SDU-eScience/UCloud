package dk.sdu.cloud.storage.api

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.client.AuthenticatedCloud
import io.tus.java.client.TusUploader
import org.slf4j.LoggerFactory
import java.io.InputStream

fun TusDescriptions.uploader(
    inputStream: InputStream,
    location: String,
    payloadSizeMax32Bit: Int,
    cloud: RefreshingJWTAuthenticatedCloud
): TusUploader {
    return uploader(
        inputStream,
        location,
        payloadSizeMax32Bit,
        cloud.parent,
        cloud.tokenRefresher.retrieveTokenRefreshIfNeeded()
    )
}

fun TusDescriptions.uploader(
    inputStream: InputStream,
    location: String,
    payloadSizeMax32Bit: Int,
    cloud: AuthenticatedCloud,
    tokenRefresher: RefreshingJWTAuthenticator
): TusUploader {
    return uploader(
        inputStream,
        location,
        payloadSizeMax32Bit,
        cloud.parent,
        tokenRefresher.retrieveTokenRefreshIfNeeded()
    )
}

fun TusUploader.start(bytesUploadedCallback: ((Long) -> Unit)? = null) {
    log.debug("Starting upload: $this")
    while (true) {
        log.debug("Uploading chunk...")
        val bytesRead = uploadChunk()
        log.debug("Read $bytesRead from upload")
        if (bytesUploadedCallback != null) bytesUploadedCallback(offset)
        if (bytesRead == -1) break
        log.debug("End of loop")
    }
    log.debug("Exiting...")
    finish()
    log.debug("Exit!")
}

private val log = LoggerFactory.getLogger("dk.sdu.cloud.storage.api.TusServiceHelpersKt")
