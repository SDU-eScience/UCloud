package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.UsageRequest
import dk.sdu.cloud.accounting.storage.api.StorageUsedResourceDescription
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.CopyRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.MoveRequest
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.ReclassifyRequest
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.SimpleBulkUpload
import dk.sdu.cloud.file.api.SimpleUploadRequest
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.api.sensitivityLevel
import dk.sdu.cloud.file.trash.api.ClearRequest
import dk.sdu.cloud.file.trash.api.FileTrashDescriptions
import dk.sdu.cloud.file.trash.api.TrashRequest
import dk.sdu.cloud.service.Loggable
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import java.nio.file.Files
import java.util.*
import java.util.zip.ZipInputStream

data class UserAndClient(val username: String, val client: AuthenticatedClient, val refreshToken: String)

/**
 * In this test suite we will perform testing of the file feature as a whole.
 *
 * The test suite starts by creating various folders and files. This will happen via various ways, starting from
 * simple to more advanced (more likely to fail) operations. The following will be performed:
 *
 * - Create directory
 * - Upload small file
 * - Upload larger file
 * - Copy files
 * - Move files
 * - Delete files (direct)
 * - Delete files (trash)
 * - Upload simple archives (no folders, zip and targz)
 * - Upload complex archives (nested folders, zip and targz)
 * - UTF8 handling
 * - Favorites (with moves and deletions)
 * - File sensitivity
 */
class FileTesting(val userA: UserAndClient, val userB: UserAndClient) {
    private val testId = UUID.randomUUID().toString()

    suspend fun runTest() {
        val users = listOf(userA, userB)

        with(users[0]) {
            createDirectoryTest()
            smallFileUploadTest()
            copyFilesTest()
            moveFilesTest()
            deleteDirectTest()
            deleteTrashTest()
            simpleArchiveTest()
            complexArchiveTest()
            sensitivityTest()
            singleDownloadTest()
            bulkDownloadTest()
            log.info("Test complete")
        }
    }

    private suspend fun UserAndClient.createDirectoryTest() {
        log.info("Testing creation of directories")

        createDir(testId) // Create the root directory for this test (all other tests will depend on this)

        // Create some directories
        createDir(testId, DirectoryTest.DIR)
        repeat(DirectoryTest.DIR_COUNT) {
            createDir(testId, DirectoryTest.DIR, "$it")
        }

        // Then test that we can find them
        val homeDir = listAt(testId)
        requireFile(
            homeDir,
            FileType.DIRECTORY,
            DirectoryTest.DIR
        )

        val dir = listAt(testId, DirectoryTest.DIR)
        repeat(DirectoryTest.DIR_COUNT) {
            requireFile(dir, FileType.DIRECTORY, "$it")
        }

        log.info("Directories successfully created!")
    }

    private suspend fun UserAndClient.smallFileUploadTest(): Unit = with(SmallFileUpload) {
        log.info("Uploading small file!")

        val fileToUpload = Files.createTempFile("", "").toFile().also { it.writeText(CONTENTS) }

        createDir(testId, DIR)
        MultiPartUploadDescriptions.simpleUpload.call(
            SimpleUploadRequest(
                joinPath(homeFolder, testId, DIR, NAME),
                BinaryStream.outgoingFromChannel(fileToUpload.readChannel(), fileToUpload.length())
            ),
            client
        ).orThrow()

        requireFile(
            listAt(testId),
            FileType.DIRECTORY,
            DIR
        )

        requireFile(
            listAt(testId, DIR),
            FileType.FILE,
            NAME
        )

        log.info("Small file uploaded!")
    }

    private suspend fun UserAndClient.copyFilesTest(): Unit = with(Copy) {
        createDir(testId, DIR)

        log.info("Testing copy of file")
        FileDescriptions.copy.call(
            CopyRequest(
                path = joinPath(
                    homeFolder,
                    testId,
                    SmallFileUpload.DIR,
                    SmallFileUpload.NAME
                ),
                newPath = joinPath(
                    homeFolder,
                    testId,
                    DIR,
                    FILE_NAME
                )
            ),
            client
        ).orThrow()
        log.info("File copied!")

        log.info("Testing copy of directory")
        FileDescriptions.copy.call(
            CopyRequest(
                path = joinPath(homeFolder, testId, SmallFileUpload.DIR),
                newPath = joinPath(
                    homeFolder,
                    testId,
                    DIR,
                    FOLDER_NAME
                )
            ),
            client
        ).orThrow()

        run {
            requireFile(listAt(testId), FileType.DIRECTORY, DIR)

            val list = listAt(testId, DIR)
            requireFile(list, FileType.FILE, FILE_NAME)
            requireFile(
                list,
                FileType.DIRECTORY,
                FOLDER_NAME
            )

            requireFile(
                listAt(
                    testId,
                    DIR,
                    FOLDER_NAME
                ),
                FileType.FILE,
                SmallFileUpload.NAME
            )
        }
        log.info("Directory copied!")
    }

    private suspend fun UserAndClient.moveFilesTest(): Unit = with(Move) {
        createDir(testId, DIR)
        createDir(
            testId,
            DIR,
            FILE_BEFORE
        )
        FileDescriptions.move.call(
            MoveRequest(
                path = joinPath(
                    homeFolder,
                    testId,
                    DIR,
                    FILE_BEFORE
                ),
                newPath = joinPath(
                    homeFolder,
                    testId,
                    DIR,
                    FILE_AFTER
                )
            ),
            client
        ).orThrow()

        run {
            requireFile(listAt(testId), FileType.DIRECTORY, DIR)

            val list = listAt(testId, DIR)
            requireFile(
                list,
                FileType.DIRECTORY,
                FILE_AFTER
            )
        }
    }

    private suspend fun UserAndClient.deleteDirectTest(): Unit = with(DeleteDirect) {
        createDir(testId, DIR)
        val fileToDelete = joinPath(
            homeFolder,
            testId,
            DIR,
            FILE_TO_DELETE
        )
        val dirToDelete = joinPath(
            homeFolder,
            testId,
            DIR,
            DIR_TO_DELETE
        )

        FileDescriptions.copy.call(
            CopyRequest(
                path = joinPath(
                    homeFolder,
                    testId,
                    SmallFileUpload.DIR,
                    SmallFileUpload.NAME
                ),
                newPath = fileToDelete
            ),
            client
        ).orThrow()

        FileDescriptions.copy.call(
            CopyRequest(
                path = joinPath(homeFolder, testId, SmallFileUpload.DIR),
                newPath = dirToDelete
            ),
            client
        ).orThrow()

        FileDescriptions.deleteFile.call(
            DeleteFileRequest(fileToDelete),
            client
        ).orThrow()

        FileDescriptions.deleteFile.call(
            DeleteFileRequest(dirToDelete),
            client
        ).orThrow()

        run {
            requireFile(
                listAt(testId), FileType.DIRECTORY,
                DIR
            )
            val list = listAt(testId, DIR)
            if (list.isNotEmpty()) throw IllegalArgumentException("Some files were not deleted correctly!")
        }
    }

    private suspend fun UserAndClient.deleteTrashTest(): Unit = with(DeleteTrash) {
        createDir(testId, DIR)
        val fileToDelete = joinPath(
            homeFolder,
            testId,
            DIR,
            FILE_TO_DELETE
        )
        val dirToDelete = joinPath(
            homeFolder,
            testId,
            DIR,
            DIR_TO_DELETE
        )

        FileDescriptions.copy.call(
            CopyRequest(
                path = joinPath(
                    homeFolder,
                    testId,
                    SmallFileUpload.DIR,
                    SmallFileUpload.NAME
                ),
                newPath = fileToDelete
            ),
            client
        ).orThrow()

        FileDescriptions.copy.call(
            CopyRequest(
                path = joinPath(homeFolder, testId, SmallFileUpload.DIR),
                newPath = dirToDelete
            ),
            client
        ).orThrow()

        FileTrashDescriptions.trash.call(
            TrashRequest(listOf(fileToDelete, dirToDelete)),
            client
        ).orThrow()

        FileTrashDescriptions.clear.call(ClearRequest(), client).orThrow()

        retrySection(attempts = 360, delay = 1000) {
            requireFile(
                listAt(testId), FileType.DIRECTORY,
                DIR
            )
            val list = listAt(testId, DIR)
            if (list.isNotEmpty()) throw IllegalArgumentException("Some files were not deleted correctly!")
        }
    }

    private suspend fun UserAndClient.simpleArchiveTest(): Unit = with(SimpleArchive) {
        createDir(testId, DIR)
        createDir(
            testId,
            DIR,
            DIR_ZIP
        )
        createDir(
            testId,
            DIR,
            DIR_TGZ
        )

        run {
            log.info("Uploading zip file")
            val zipFile = Files.createTempFile("", ".zip").toFile()
            javaClass.classLoader.getResourceAsStream("simple.zip").use { ins ->
                zipFile.outputStream().use { outs ->
                    ins!!.copyTo(outs)
                }
            }

            MultiPartUploadDescriptions.simpleBulkUpload.call(
                SimpleBulkUpload(
                    location = joinPath(
                        homeFolder,
                        testId,
                        DIR,
                        DIR_ZIP
                    ),
                    policy = WriteConflictPolicy.OVERWRITE,
                    format = "zip",
                    file = BinaryStream.outgoingFromChannel(zipFile.readChannel())
                ),
                client
            ).orThrow()
            log.info("Zip file uploaded")
        }

        run {
            log.info("Uploading tgz file")
            val tgzFile = Files.createTempFile("", ".tar.gz").toFile()
            javaClass.classLoader.getResourceAsStream("simple.tar.gz").use { ins ->
                tgzFile.outputStream().use { outs ->
                    ins!!.copyTo(outs)
                }
            }

            MultiPartUploadDescriptions.simpleBulkUpload.call(
                SimpleBulkUpload(
                    location = joinPath(
                        homeFolder,
                        testId,
                        DIR,
                        DIR_TGZ
                    ),
                    policy = WriteConflictPolicy.OVERWRITE,
                    format = "tgz",
                    file = BinaryStream.outgoingFromChannel(tgzFile.readChannel())
                ),
                client
            ).orThrow()
            log.info("tgz file uploaded")
        }

        run {
            requireFile(
                listAt(testId), FileType.DIRECTORY,
                DIR
            )

            retrySection {
                val zipList = listAt(
                    testId,
                    DIR,
                    DIR_ZIP
                )
                (1..10).forEach {
                    requireFile(zipList, FileType.FILE, "$it")
                }

                val tgzList = listAt(
                    testId,
                    DIR,
                    DIR_TGZ
                )
                (1..10).forEach {
                    requireFile(tgzList, FileType.FILE, "$it")
                }
            }
        }
    }

    private suspend fun UserAndClient.complexArchiveTest(): Unit = with(ComplexArchive) {
        createDir(testId, DIR)
        createDir(
            testId,
            DIR,
            DIR_ZIP
        )
        createDir(
            testId,
            DIR,
            DIR_TGZ
        )

        run {
            log.info("Uploading zip file")
            val zipFile = Files.createTempFile("", ".zip").toFile()
            javaClass.classLoader.getResourceAsStream("complex.zip").use { ins ->
                zipFile.outputStream().use { outs ->
                    ins!!.copyTo(outs)
                }
            }

            MultiPartUploadDescriptions.simpleBulkUpload.call(
                SimpleBulkUpload(
                    location = joinPath(
                        homeFolder,
                        testId,
                        DIR,
                        DIR_ZIP
                    ),
                    policy = WriteConflictPolicy.OVERWRITE,
                    format = "zip",
                    file = BinaryStream.outgoingFromChannel(zipFile.readChannel())
                ),
                client
            ).orThrow()
            log.info("Zip file uploaded")
        }

        run {
            log.info("Uploading tgz file")
            val tgzFile = Files.createTempFile("", ".tar.gz").toFile()
            javaClass.classLoader.getResourceAsStream("complex.tar.gz").use { ins ->
                tgzFile.outputStream().use { outs ->
                    ins!!.copyTo(outs)
                }
            }

            MultiPartUploadDescriptions.simpleBulkUpload.call(
                SimpleBulkUpload(
                    location = joinPath(
                        homeFolder,
                        testId,
                        DIR,
                        DIR_TGZ
                    ),
                    policy = WriteConflictPolicy.OVERWRITE,
                    format = "tgz",
                    file = BinaryStream.outgoingFromChannel(tgzFile.readChannel())
                ),
                client
            ).orThrow()
            log.info("tgz file uploaded")
        }

        run {
            requireFile(
                listAt(testId),
                FileType.DIRECTORY,
                DIR
            )

            val list = listAt(testId, DIR)
            requireFile(
                list, FileType.DIRECTORY,
                DIR_ZIP
            )
            requireFile(
                list, FileType.DIRECTORY,
                DIR_TGZ
            )

            suspend fun testDir(vararg components: String) {
                val path1 = listOf("A", "B", "C", "D", "foo")
                val path2 = listOf("1", "2", "3", "4", "foo")

                suspend fun findInPath(path: List<String>) {
                    path.indices.forEach { i ->
                        if (i == path.lastIndex) return@forEach

                        val atDir = listAt(testId, *components, *path.take(i).toTypedArray())
                        val next = joinPath(homeFolder, testId, *components, *path.take(i + 1).toTypedArray())

                        if (!atDir.map { it.path.normalize() }.contains(next.normalize())) {
                            throw IllegalArgumentException("Could not find $next")
                        }
                    }
                }

                findInPath(path1)
                findInPath(path2)
            }

            retrySection {
                testDir(
                    DIR,
                    DIR_ZIP
                )
                testDir(
                    DIR,
                    DIR_TGZ
                )
            }
        }
    }

    private suspend fun UserAndClient.sensitivityTest(): Unit = with(SensitivityTest) {
        createDir(testId, DIR)

        val filePath = joinPath(
            homeFolder,
            testId,
            DIR,
            FILE_TO_TEST
        )
        FileDescriptions.copy.call(
            CopyRequest(
                path = joinPath(
                    homeFolder,
                    testId,
                    SmallFileUpload.DIR,
                    SmallFileUpload.NAME
                ),
                newPath = filePath
            ),
            client
        ).orThrow()


        run {
            val startSensitivity = FileDescriptions.stat.call(StatRequest(filePath), client).orThrow().sensitivityLevel

            require(SensitivityLevel.PRIVATE == startSensitivity)

            FileDescriptions.reclassify.call(
                ReclassifyRequest(
                    filePath,
                    SensitivityLevel.SENSITIVE
                ),
                client
            ).orThrow()

            val newSensitivity = FileDescriptions.stat.call(StatRequest(filePath), client).orThrow().sensitivityLevel
            require(SensitivityLevel.SENSITIVE == newSensitivity)

            val copyPath = joinPath(
                homeFolder,
                testId,
                DIR,
                FILE_COPY
            )
            FileDescriptions.copy.call(
                CopyRequest(
                    path = filePath,
                    newPath = copyPath
                ),
                client
            ).orThrow()

            val copiedSensitivity = FileDescriptions.stat.call(StatRequest(copyPath), client).orThrow().sensitivityLevel
            require(SensitivityLevel.SENSITIVE == copiedSensitivity)
        }
    }

    private suspend fun UserAndClient.accountingTest() {
        log.info("Running accounting test")
        val usage = StorageUsedResourceDescription.usage.call(
            UsageRequest,
            client
        ).orThrow()

        // We cannot know for sure how much storage is taken up by this. It depends on a few factors, such as
        // the default files. As a result we allow for this number to be within a range.
        if (usage.usage !in 100..500) {
            throw IllegalStateException("Unexpected amount of storage used after test. We got back: ${usage.usage}")
        }

        // We cannot test the charting endpoint for useful results since a scan is only run every few hours
        // But we test it anyway to see if we get a successful response

        log.info("Accounting test done")
    }

    private suspend fun UserAndClient.singleDownloadTest() {
        log.info("Testing single download")
        val outputFile = Files.createTempFile("", "").toFile()
        FileDescriptions.download
            .call(
                DownloadByURI(
                    joinPath(
                        homeFolder,
                        testId,
                        SmallFileUpload.DIR,
                        SmallFileUpload.NAME
                    ), token = null
                ),
                client
            )
            .orThrow()
            .asIngoing()
            .channel
            .toInputStream()
            .copyTo(outputFile.outputStream())

        val readText = outputFile.readText()
        if (readText != SmallFileUpload.CONTENTS) {
            throw IllegalStateException(
                "Expected file to contain '${SmallFileUpload.CONTENTS}' but instead it " +
                        "contains '$readText'"
            )
        }
    }

    private suspend fun UserAndClient.bulkDownloadTest() {
        val outputFile = Files.createTempFile("", ".zip").toFile()
        FileDescriptions.download
            .call(
                DownloadByURI(
                    joinPath(
                        homeFolder,
                        testId,
                        SimpleArchive.DIR
                    ), token = null
                ),
                client
            )
            .orThrow()
            .asIngoing()
            .channel
            .toInputStream()
            .copyTo(outputFile.outputStream())

        // TODO Check that zip file contains the correct files!

        val foundNames = ArrayList<String>()
        ZipInputStream(outputFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                foundNames += entry.name
                log.info("Unpacking: ${entry.name} of ${entry.size} bytes")
                log.info(zipIn.readBytes().toString(Charsets.UTF_8))

                entry = zipIn.nextEntry
                log.info("Looking for next entry")
            }
        }

        log.info("Done unpacking")
        (1..10).forEach { filename ->
            require(foundNames.map { it.fileName() }.any { it == filename.toString() })
        }
    }

    companion object : Loggable {
        override val log = logger()

        object DirectoryTest {
            const val DIR = "DirectoryTesting"
            const val DIR_COUNT = 5
        }

        object SmallFileUpload {
            const val DIR = "SmallFileUpload"
            const val NAME = "file.txt"
            val CONTENTS = "Hello, World 🎉!"
        }

        object Copy {
            const val DIR = "Copy"
            const val FILE_NAME = "copy_file.txt"
            const val FOLDER_NAME = "copy_folder"
        }

        object Move {
            const val DIR = "Move"
            const val FILE_BEFORE = "A"
            const val FILE_AFTER = "B"
        }

        object DeleteDirect {
            const val DIR = "DeleteDirect"
            const val FILE_TO_DELETE = "File"
            const val DIR_TO_DELETE = "Dir"
        }

        object DeleteTrash {
            const val DIR = "DeleteTrash"
            const val FILE_TO_DELETE = "File"
            const val DIR_TO_DELETE = "Dir"
        }

        object SimpleArchive {
            const val DIR = "SimpleArchive"
            const val DIR_ZIP = "ZIP"
            const val DIR_TGZ = "TGZ"
        }

        object ComplexArchive {
            const val DIR = "ComplexArchive"
            const val DIR_ZIP = "ZIP"
            const val DIR_TGZ = "TGZ"
        }

        object SensitivityTest {
            const val DIR = "Sensitivity"
            const val FILE_TO_TEST = "file.txt"
            const val FILE_COPY = "copy.txt"
        }
    }
}

/**
 * Utility code for retrying a section multiple times. This is useful for testing async code.
 */
inline fun <T> retrySection(attempts: Int = 5, delay: Long = 500, block: () -> T): T {
    for (i in 1..attempts) {
        @Suppress("TooGenericExceptionCaught")
        try {
            return block()
        } catch (ex: Throwable) {
            if (i == attempts) throw ex
            Thread.sleep(delay)
        }
    }
    throw IllegalStateException("retrySection impossible situation reached. This should not happen.")
}
