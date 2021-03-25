package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesCopyRequestItem
import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.file.ucloud.createHome
import dk.sdu.cloud.file.ucloud.*
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatInstance
import io.mockk.InternalPlatformDsl.toStr
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import java.util.*
import kotlin.test.*

class CopyTaskTest {
    private fun createStorageTask(
        request: BulkRequest<FilesCopyRequestItem>,
        hardLimitReached: Boolean = false,
    ): StorageTask {
        return StorageTask(
            UUID.randomUUID().toStr(),
            Files.copy.fullName,
            defaultMapper.encodeToJsonElement(CopyTaskRequirements(1, hardLimitReached)) as JsonObject,
            defaultMapper.encodeToJsonElement(request) as JsonObject,
            JsonObject(emptyMap()),
            TestUsers.user.username,
            Time.now()
        )
    }

    @Test
    fun `test copy of single file`() = t {
        val sourceFile: InternalFile
        val destinationFile: InternalFile
        createHome(TestUsers.user.username).apply {
            sourceFile = createFile("fie.txt")
            destinationFile = pointerToFile("fie2.txt")
        }

        val task = createStorageTask(bulkRequestOf(
            FilesCopyRequestItem(
                fileQueries.convertInternalFileToUCloudPath(sourceFile).path,
                fileQueries.convertInternalFileToUCloudPath(destinationFile).path,
                WriteConflictPolicy.REPLACE
            )
        ))

        assertTrue(copyTask.canHandle(Actor.System, Files.copy.fullName, task.rawRequest))

        copyTask.execute(
            Actor.System,
            task
        )

        // TODO Assert that the files were created (Missing API for this right now)
    }

    @Test
    fun `test copy of directory, no nesting, no concurrency`() = t {
        val sourceFile: InternalFile
        val destinationFile: InternalFile
        createHome(TestUsers.user.username).apply {
            sourceFile = createDirectory("source").apply {
                repeat(10_000) {
                    createFile("file-$it")
                }
            }
            destinationFile = pointerToFile("destination")
        }

        val task = createStorageTask(
            bulkRequestOf(
                FilesCopyRequestItem(
                    fileQueries.convertInternalFileToUCloudPath(sourceFile).path,
                    fileQueries.convertInternalFileToUCloudPath(destinationFile).path,
                    WriteConflictPolicy.REPLACE
                )
            )
        )

        assertTrue(copyTask.canHandle(Actor.System, Files.copy.fullName, task.rawRequest))
        copyTask.execute(Actor.System, task)
    }

    @Test
    fun `test copy of directory, no nesting, with concurrency`() = t {
        val sourceFile: InternalFile
        val destinationFile: InternalFile
        createHome(TestUsers.user.username).apply {
            sourceFile = createDirectory("source").apply {
                repeat(10_000) {
                    createFile("file-$it")
                }
            }
            destinationFile = pointerToFile("destination")
        }

        val task = createStorageTask(
            bulkRequestOf(
                FilesCopyRequestItem(
                    fileQueries.convertInternalFileToUCloudPath(sourceFile).path,
                    fileQueries.convertInternalFileToUCloudPath(destinationFile).path,
                    WriteConflictPolicy.REPLACE
                )
            ),
            hardLimitReached = true
        )

        assertTrue(copyTask.canHandle(Actor.System, Files.copy.fullName, task.rawRequest))
        copyTask.execute(Actor.System, task)
    }

    @Test
    fun `test copy of directory, with nesting, with concurrency`() = t {
        val numberOfDirs = 10
        val numberOfFilesPerDir = 10
        val sourceFile: InternalFile
        val destinationFile: InternalFile
        createHome(TestUsers.user.username).apply {
            sourceFile = createDirectory("source").apply {
                repeat(numberOfDirs) {
                    createDirectory("dir-$it").apply {
                        repeat(numberOfFilesPerDir) {
                            createFile("file-$it")
                        }
                    }
                }
            }
            destinationFile = pointerToFile("destination")
        }

        val task = createStorageTask(
            bulkRequestOf(
                FilesCopyRequestItem(
                    fileQueries.convertInternalFileToUCloudPath(sourceFile).path,
                    fileQueries.convertInternalFileToUCloudPath(destinationFile).path,
                    WriteConflictPolicy.REPLACE
                )
            ),
            hardLimitReached = true
        )

        assertTrue(copyTask.canHandle(Actor.System, Files.copy.fullName, task.rawRequest))
        copyTask.execute(Actor.System, task)
        repeat(numberOfDirs) {
            val dir = File(destinationFile.path, "dir-$it")
            val dirList = dir.list()
            assertThatInstance(dir, "Should contain a directory (dir-$it)") { dir.exists() }
            assertThatInstance(dirList, "Should contain $numberOfFilesPerDir files") { it.size == numberOfFilesPerDir }
        }
    }
}
