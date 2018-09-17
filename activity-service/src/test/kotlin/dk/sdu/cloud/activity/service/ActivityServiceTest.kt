package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityStreamEntry
import dk.sdu.cloud.activity.api.CountedFileActivityOperation
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.activity.services.ActivityStream
import dk.sdu.cloud.activity.services.ActivityStreamDao
import dk.sdu.cloud.activity.services.ActivityStreamSubject
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals

class ActivityServiceTest {
    private fun initStreamConversionTest(): Pair<ActivityService<Unit>, ActivityStreamDao<Unit>> {
        val streamDao: ActivityStreamDao<Unit> = mockk(relaxed = true)
        return Pair(
            ActivityService(
                mockk(relaxed = true),
                streamDao,
                mockk(relaxed = true)
            ),
            streamDao
        )
    }

    @Test
    fun `stream conversion - file report - counting downloads of same file`() {
        val (service, dao) = initStreamConversionTest()
        val fileId = "fileId"
        val downloadCount = 10
        val events = (0 until downloadCount).map { ActivityEvent.Download("user", System.currentTimeMillis(), fileId) }
        service.insertBatch(Unit, events)

        verify {
            dao.insertBatchIntoStream(
                any(),

                ActivityStream(ActivityStreamSubject.File(fileId)),

                match { events ->
                    assertEquals(1, events.size)
                    val entry = events.first() as ActivityStreamEntry.Counted
                    assertEquals(entry.operation, CountedFileActivityOperation.DOWNLOAD)
                    assertEquals(downloadCount, entry.count)
                    assertEquals(fileId, entry.file.id)
                    assertEquals(events.minBy { it.timestamp }!!.timestamp, entry.timestamp)

                    true
                }
            )
        }
    }

    @Test
    fun `stream conversion - file report - counting downloads of different files`() {
        data class FileDownload(val fileId: String, val count: Int)

        val (service, dao) = initStreamConversionTest()
        val downloads = listOf(FileDownload("a", 10), FileDownload("b", 5))
        val events = downloads.flatMap { download ->
            (0 until download.count).map {
                ActivityEvent.Download(
                    "user",
                    System.currentTimeMillis(),
                    download.fileId
                )
            }
        }
        service.insertBatch(Unit, events)

        verify {
            downloads.forEach { file ->
                dao.insertBatchIntoStream(
                    any(),

                    ActivityStream(ActivityStreamSubject.File(file.fileId)),

                    match { events ->
                        assertEquals(1, events.size)
                        val entry = events.first() as ActivityStreamEntry.Counted
                        assertEquals(entry.operation, CountedFileActivityOperation.DOWNLOAD)
                        assertEquals(file.count, entry.count)
                        assertEquals(file.fileId, entry.file.id)
                        assertEquals(events.minBy { it.timestamp }!!.timestamp, entry.timestamp)

                        true
                    }
                )
            }
        }
    }

    @Test
    fun `stream conversion - user report - counting downloads of same file`() {
        val (service, dao) = initStreamConversionTest()
        val fileId = "fileId"
        val user = "user"
        val downloadCount = 10
        val events = (0 until downloadCount).map { ActivityEvent.Download(user, System.currentTimeMillis(), fileId) }
        service.insertBatch(Unit, events)

        verify {
            dao.insertBatchIntoStream(
                any(),

                ActivityStream(ActivityStreamSubject.User(user)),

                match { events ->
                    assertEquals(1, events.size)
                    val entry = events.first() as ActivityStreamEntry.Counted
                    assertEquals(entry.operation, CountedFileActivityOperation.DOWNLOAD)
                    assertEquals(downloadCount, entry.count)
                    assertEquals(fileId, entry.file.id)
                    assertEquals(events.minBy { it.timestamp }!!.timestamp, entry.timestamp)

                    true
                }
            )
        }
    }

    @Test
    fun `stream conversion - user report - counting downloads of different files`() {
        data class FileDownload(val fileId: String, val count: Int)

        val (service, dao) = initStreamConversionTest()
        val downloads = listOf(FileDownload("a", 10), FileDownload("b", 5))
        val user = "user"
        val events = downloads.flatMap { download ->
            (0 until download.count).map {
                ActivityEvent.Download(
                    user,
                    System.currentTimeMillis(),
                    download.fileId
                )
            }
        }
        service.insertBatch(Unit, events)

        verify {
            dao.insertBatchIntoStream(
                any(),

                ActivityStream(ActivityStreamSubject.User(user)),

                match { events ->
                    assertEquals(2, events.size)

                    downloads.forEach { file ->
                        val entry =
                            events.find { (it as ActivityStreamEntry.Counted).file.id == file.fileId }!! as ActivityStreamEntry.Counted
                        assertEquals(entry.operation, CountedFileActivityOperation.DOWNLOAD)
                        assertEquals(file.count, entry.count)
                        assertEquals(file.fileId, entry.file.id)
                        assertEquals(events.minBy { it.timestamp }!!.timestamp, entry.timestamp)
                    }

                    true
                }
            )
        }
    }
}