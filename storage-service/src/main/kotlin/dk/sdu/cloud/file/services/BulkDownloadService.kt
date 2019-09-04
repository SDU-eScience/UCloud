package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarHeader
import org.kamranzafar.jtar.TarOutputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

private const val DEFAULT_FILE_PERMISSION = 511

class BulkDownloadService<Ctx : FSUserContext>(
    private val fs: CoreFileSystemService<Ctx>
) {
    /**
     * @param filesDownloadedOutput Will write which files ids are downloaded to this list. Useful for auditing.
     */
    suspend fun downloadFiles(
        ctx: Ctx,
        prefixPath: String,
        listOfFiles: List<String>,
        target: OutputStream,
        filesDownloadedOutput: ArrayList<String?>? = null
    ) {
        TarOutputStream(GZIPOutputStream(target)).use { tarStream ->
            for (path in listOfFiles) {
                try {
                    // Calculate correct path, check if file exists and filter out bad files
                    val absPath = "${prefixPath.removeSuffix("/")}/${path.removePrefix("/")}"
                    val stat = fs.statOrNull(
                        ctx,
                        absPath,
                        setOf(
                            FileAttribute.INODE,
                            FileAttribute.PATH,
                            FileAttribute.SIZE,
                            FileAttribute.TIMESTAMPS,
                            FileAttribute.FILE_TYPE
                        )
                    )

                    if (stat == null) {
                        filesDownloadedOutput?.add(null)
                    } else {
                        filesDownloadedOutput?.add(stat.inode)

                        // Write tar header
                        log.debug("Writing tar header: ($path, $stat)")
                        tarStream.putNextEntry(
                            TarEntry(
                                TarHeader.createHeader(
                                    path,
                                    stat.size,
                                    stat.timestamps.modified,
                                    stat.fileType == FileType.DIRECTORY,
                                    DEFAULT_FILE_PERMISSION // TODO! (0777)
                                )
                            )
                        )

                        // Write file contents
                        fs.read(ctx, absPath) { copyTo(tarStream) }
                    }
                } catch (ex: FSException) {
                    when (ex) {
                        is FSException.NotFound, is FSException.PermissionException -> {
                            log.debug("Skipping file, caused by exception:")
                            log.debug(ex.stackTraceToString())
                        }

                        else -> throw ex
                    }
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
