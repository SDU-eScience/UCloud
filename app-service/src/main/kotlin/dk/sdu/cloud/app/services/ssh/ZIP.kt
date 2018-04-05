package dk.sdu.cloud.app.services.ssh

import dk.sdu.cloud.app.util.BashEscaper
import org.slf4j.LoggerFactory

fun SSHConnection.createZipFileOfDirectory(outputPath: String, inputDirectoryPath: String): Int {
    log.debug("Creating zip file of directory: outputPath=$outputPath, inputDirectoryPath=$inputDirectoryPath")
    val (status, output) = execWithOutputAsText(
        "zip -r " +
                BashEscaper.safeBashArgument(outputPath) + " " +
                BashEscaper.safeBashArgument(inputDirectoryPath)
    )

    if (status != 0) {
        log.warn("Unable to create ZIP file: outputPath=$outputPath, inputDirectoryPath=$inputDirectoryPath")
        log.warn("Status: $status, Output: $output")
    }

    return status
}

private val log = LoggerFactory.getLogger("dk.sdu.cloud.app.services.ssh.ZIPKt")