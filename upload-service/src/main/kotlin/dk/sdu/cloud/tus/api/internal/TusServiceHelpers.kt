package dk.sdu.cloud.tus.api.internal

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.tus.api.TusDescriptions
import io.tus.java.client.TusUploader
import java.io.InputStream

fun TusDescriptions.uploader(
        inputStream: InputStream,
        location: String,
        payloadSizeMax32Bit: Int,
        cloud: RefreshingJWTAuthenticator
): TusUploader {
    return uploader(inputStream, location, payloadSizeMax32Bit, cloud.parent, cloud.retrieveTokenRefreshIfNeeded())
}

fun TusUploader.start(bytesUploadedCallback: ((Long) -> Unit)? = null) {
    do {
        if (bytesUploadedCallback != null) bytesUploadedCallback(offset)
    } while (uploadChunk() > -1)
    finish()
}
