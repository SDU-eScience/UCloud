package dk.sdu.cloud.app.abacus.services.ssh

import dk.sdu.cloud.app.abacus.services.ssh.SSH.log
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.service.BashEscaper

suspend fun SSHConnection.createZipFileOfDirectory(outputPath: String, inputDirectoryPath: String): Int {
    log.debug("Creating zip file of directory: outputPath=$outputPath, inputDirectoryPath=$inputDirectoryPath")
    val workingDirectory = inputDirectoryPath.parent()
    val inputDirectoryName = inputDirectoryPath.fileName()
    val (status, output) = execWithOutputAsText(
        "cd ${BashEscaper.safeBashArgument(workingDirectory)} && zip -r " +
                BashEscaper.safeBashArgument(outputPath) + " " +
                BashEscaper.safeBashArgument(inputDirectoryName)
    )

    if (status != 0) {
        log.warn("Unable to create ZIP file: outputPath=$outputPath, inputDirectoryPath=$inputDirectoryPath")
        log.warn("Status: $status, Output: $output")
    }

    return status
}

/**
 * Returns 0 on success. 1 or 2 with warnings, 3 or above with severe errors.
 */

private const val WARNING1 = 1
private const val WARNING2 = 2
private const val SEVERE_ERROR = 3

suspend fun SSHConnection.unzip(zipPath: String, targetPath: String): Int {
    log.debug("Unzipping file zipPath=$zipPath, targetPath=$targetPath")
    val (status, output) = execWithOutputAsText(
        "unzip " +
                BashEscaper.safeBashArgument(zipPath) + " " +
                "-d " + BashEscaper.safeBashArgument(targetPath)
    )

    if (status in WARNING1..WARNING2) {
        log.debug("A warning was produced when unzipping file")
    } else if (status >= SEVERE_ERROR) {
        log.warn("Unable to unzip file: zipPath=$zipPath, targetPath=$targetPath")
        log.warn("Status: $status, Output: $output")
    }

    return status
}

