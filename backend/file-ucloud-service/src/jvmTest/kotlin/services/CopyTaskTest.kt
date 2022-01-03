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
import dk.sdu.cloud.file.ucloud.services.tasks.CopyTaskRequirements
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatInstance
import io.mockk.InternalPlatformDsl.toStr
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
            TaskRequirements(
                hardLimitReached,
                defaultMapper.encodeToJsonElement(CopyTaskRequirements(1)) as JsonObject
            ),
            defaultMapper.encodeToJsonElement(request) as JsonObject,
            JsonObject(emptyMap()),
            TestUsers.user.username,
            Time.now()
        )
    }

    @Test
    fun `test copy of single file`() = t {
        with(copyTask) {
            val sourceFile: InternalFile
            val destinationFile: InternalFile
            createHome(TestUsers.user.username).apply {
                sourceFile = createFile("fie.txt")
                destinationFile = pointerToFile("fie2.txt")
            }

            val task = createStorageTask(bulkRequestOf(
                FilesCopyRequestItem(
                    pathConverter.internalToUCloud(sourceFile).path,
                    pathConverter.internalToUCloud(destinationFile).path,
                    WriteConflictPolicy.REPLACE
                )
            ))

            assertTrue(taskContext.canHandle(Actor.System, Files.copy.fullName, task.rawRequest))

            taskContext.execute(
                Actor.System,
                task
            )

            // TODO Assert that the files were created (Missing API for this right now)
        }
    }

    @Test
    fun `test copy of directory, no nesting, no concurrency`() = t {
        with(copyTask) {
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
                        pathConverter.internalToUCloud(sourceFile).path,
                        pathConverter.internalToUCloud(destinationFile).path,
                        WriteConflictPolicy.REPLACE
                    )
                )
            )

            assertTrue(taskContext.canHandle(Actor.System, Files.copy.fullName, task.rawRequest))
            taskContext.execute(Actor.System, task)
        }
    }

    @Test
    fun `test copy of directory, no nesting, with concurrency`() = t {
        with(copyTask) {
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
                        pathConverter.internalToUCloud(sourceFile).path,
                        pathConverter.internalToUCloud(destinationFile).path,
                        WriteConflictPolicy.REPLACE
                    )
                ),
                hardLimitReached = true
            )

            assertTrue(taskContext.canHandle(Actor.System, Files.copy.fullName, task.rawRequest))
            taskContext.execute(Actor.System, task)
        }
    }

    @Test
    fun `test copy of directory, with nesting, with concurrency`() = t {
        with(copyTask) {
            val numberOfDirs = 100
            val numberOfFilesPerDir = 1_000
            val sourceFile: InternalFile
            val destinationFile: InternalFile
            createHome(TestUsers.user.username).apply {
                sourceFile = createDirectory("source").apply {
                    repeat(numberOfDirs) { outer ->
                        createDirectory("dir-$outer").apply {
                            repeat(numberOfFilesPerDir) {
                                createFile("file-$outer-$it")
                            }
                        }
                    }
                }
                destinationFile = pointerToFile("destination")
            }

            val task = createStorageTask(
                bulkRequestOf(
                    FilesCopyRequestItem(
                        pathConverter.internalToUCloud(sourceFile).path,
                        pathConverter.internalToUCloud(destinationFile).path,
                        WriteConflictPolicy.REPLACE
                    )
                ),
                hardLimitReached = true
            )

            assertTrue(taskContext.canHandle(Actor.System, Files.copy.fullName, task.rawRequest))
            taskContext.execute(Actor.System, task)
            repeat(numberOfDirs) {
                val dir = File(destinationFile.path, "dir-$it")
                val dirList = dir.list()
                assertThatInstance(dir, "Should contain a directory (dir-$it)") { dir.exists() }
                assertThatInstance(dirList,
                    "Should contain $numberOfFilesPerDir files") { it.size == numberOfFilesPerDir }
            }
        }
    }

    @Test
    fun `test copy with conflict and rejection`() = t {
        with(copyTask) {
            val sourceFile: InternalFile
            val destinationFile: InternalFile
            val file1Payload = "original fie".encodeToByteArray()
            val file2Payload = "original fie2".encodeToByteArray()
            createHome(TestUsers.user.username).apply {
                sourceFile = createFile("fie", file1Payload)
                destinationFile = createFile("fie2", file2Payload)
            }

            val task = createStorageTask(
                bulkRequestOf(
                    FilesCopyRequestItem(
                        pathConverter.internalToUCloud(sourceFile).path,
                        pathConverter.internalToUCloud(destinationFile).path,
                        WriteConflictPolicy.REJECT
                    )
                )
            )

            taskContext.execute(Actor.System, task)
            assertTrue(File(sourceFile.path).readBytes().contentEquals(file1Payload))
            assertTrue(File(destinationFile.path).readBytes().contentEquals(file2Payload))
        }
    }

    @Test
    fun `test copy with conflict and replace`() = t {
        with(copyTask) {
            val sourceFile: InternalFile
            val destinationFile: InternalFile
            val file1Payload = "original fie".encodeToByteArray()
            val file2Payload = "original fie2".encodeToByteArray()
            createHome(TestUsers.user.username).apply {
                sourceFile = createFile("fie", file1Payload)
                destinationFile = createFile("fie2", file2Payload)
            }

            val task = createStorageTask(
                bulkRequestOf(
                    FilesCopyRequestItem(
                        pathConverter.internalToUCloud(sourceFile).path,
                        pathConverter.internalToUCloud(destinationFile).path,
                        WriteConflictPolicy.REPLACE
                    )
                )
            )

            taskContext.execute(Actor.System, task)
            assertTrue(File(sourceFile.path).readBytes().contentEquals(file1Payload))
            assertTrue(File(destinationFile.path).readBytes().contentEquals(file1Payload))
        }
    }

    @Test
    fun `test copy with conflict and rename`() = t {
        with(copyTask) {
            val sourceFile: InternalFile
            val destinationFile: InternalFile
            val file1Payload = "original fie".encodeToByteArray()
            val file2Payload = "original fie2".encodeToByteArray()
            val home = createHome(TestUsers.user.username).apply {
                sourceFile = createFile("fie", file1Payload)
                destinationFile = createFile("fie2", file2Payload)
            }

            val task = createStorageTask(
                bulkRequestOf(
                    FilesCopyRequestItem(
                        pathConverter.internalToUCloud(sourceFile).path,
                        pathConverter.internalToUCloud(destinationFile).path,
                        WriteConflictPolicy.RENAME
                    )
                )
            )

            taskContext.execute(Actor.System, task)
            assertTrue(File(sourceFile.path).readBytes().contentEquals(file1Payload))
            assertTrue(File(destinationFile.path).readBytes().contentEquals(file2Payload))
            assertTrue(File(home.path, "fie2(1)").readBytes().contentEquals(file1Payload))
        }
    }
}
