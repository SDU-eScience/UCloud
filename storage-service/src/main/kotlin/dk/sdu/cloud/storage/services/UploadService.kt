package dk.sdu.cloud.storage.services

import java.io.OutputStream

class UploadService(
    private val fs: FileSystemService,
    private val checksumService: ChecksumService
) {
    fun upload(user: String, path: String, writer: OutputStream.() -> Unit) {
        if (path.contains("\n")) throw FileSystemException.BadRequest("Bad filename")

        fs.write(user, path, writer)
        checksumService.computeAndAttachChecksum(user, path)
    }
}