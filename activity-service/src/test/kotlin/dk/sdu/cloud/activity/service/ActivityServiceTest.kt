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
import kotlin.test.assertTrue

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
                    val fileEntries = entry.entries
                    assertEquals(entry.operation, CountedFileActivityOperation.DOWNLOAD)
                    assertEquals(downloadCount, fileEntries.single().count)
                    assertEquals(fileId, fileEntries.single().fileId)
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
                        assertEquals(file.count, entry.entries.single().count)
                        assertEquals(file.fileId, entry.entries.single().fileId)
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
                    val fileEntries = entry.entries
                    assertEquals(entry.operation, CountedFileActivityOperation.DOWNLOAD)
                    assertEquals(downloadCount, fileEntries.single().count)
                    assertEquals(fileId, fileEntries.single().fileId)
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
                    assertEquals(1, events.size)
                    val event = events.single() as ActivityStreamEntry.Counted

                    downloads.forEach { file ->
                        val entry = event.entries.find { it.fileId == file.fileId }!!
                        assertEquals(event.operation, CountedFileActivityOperation.DOWNLOAD)
                        assertEquals(file.count, entry.count)
                        assertEquals(file.fileId, entry.fileId)
                        assertEquals(events.minBy { it.timestamp }!!.timestamp, event.timestamp)
                    }

                    true
                }
            )
        }
    }

    @Test
    fun `stream conversion - negative favorites`() {
        val (service, dao) = initStreamConversionTest()

        val user = "user"
        val negativeCount = 5
        val positiveCount = 3
        val fileId = "fileId"
        val events =
            (0 until negativeCount).map {
                ActivityEvent.Favorite(user, false, System.currentTimeMillis(), fileId)
            } + (0 until positiveCount).map {
                ActivityEvent.Favorite(user, true, System.currentTimeMillis(), fileId)
            }

        service.insertBatch(Unit, events)

        verify(atLeast = 0, atMost = 1) {
            dao.insertBatchIntoStream(
                any(),

                ActivityStream(ActivityStreamSubject.File(fileId)),

                match { events ->
                    assertEquals(0, events.size)
                    true
                }
            )
        }
    }

    @Test
    fun `stream conversion - user report - updates`() {
        data class Update(val user: String, val fileIds: List<String>) {
            fun toEvents() = fileIds.map { ActivityEvent.Updated(user, System.currentTimeMillis(), it) }
        }

        val (service, dao) = initStreamConversionTest()
        val updates = listOf(
            Update("userA", listOf("1", "2", "3")),
            Update("userB", listOf("1")),
            Update("userC", listOf("4"))
        )
        val events = updates.flatMap { it.toEvents() }

        service.insertBatch(Unit, events)

        verify {
            updates.forEach { update ->
                dao.insertBatchIntoStream(
                    any(),

                    ActivityStream(ActivityStreamSubject.User(update.user)),

                    match { events ->
                        assertEquals(1, events.size)
                        val event = events.first() as ActivityStreamEntry.Tracked
                        assertEquals(update.fileIds.size, event.files.size)
                        val receivedFileIds = event.files.map { it}
                        assertTrue(update.fileIds.all { it in receivedFileIds })
                        true
                    }
                )
            }
        }
    }

    @Test
    fun `stream conversion - user report - renames`() {
        data class Rename(val user: String, val fileIds: List<String>) {
            fun toEvents() = fileIds.map { ActivityEvent.Moved(user, "newName", System.currentTimeMillis(), it) }
        }

        val (service, dao) = initStreamConversionTest()
        val updates = listOf(
            Rename("userA", listOf("1", "2", "3")),
            Rename("userB", listOf("1")),
            Rename("userC", listOf("4"))
        )
        val events = updates.flatMap { it.toEvents() }

        service.insertBatch(Unit, events)

        verify {
            updates.forEach { update ->
                dao.insertBatchIntoStream(
                    any(),

                    ActivityStream(ActivityStreamSubject.User(update.user)),

                    match { events ->
                        assertEquals(1, events.size)
                        val event = events.first() as ActivityStreamEntry.Tracked
                        assertEquals(update.fileIds.size, event.files.size)
                        val receivedFileIds = event.files.map { it}
                        assertTrue(update.fileIds.all { it in receivedFileIds })
                        true
                    }
                )
            }
        }
    }


    @Test
    fun `stream conversion - file report - updates`() {
        data class Update(val fileId: String, val users: List<String>) {
            fun toEvents() = users.map { ActivityEvent.Updated(it, System.currentTimeMillis(), fileId) }
        }

        val (service, dao) = initStreamConversionTest()
        val updates = listOf(
            Update("file1", listOf("1", "2", "3")),
            Update("file2", listOf("1")),
            Update("file3", listOf("4"))
        )
        val events = updates.flatMap { it.toEvents() }

        service.insertBatch(Unit, events)

        verify {
            updates.forEach { update ->
                dao.insertBatchIntoStream(
                    any(),

                    ActivityStream(ActivityStreamSubject.File(update.fileId)),

                    match { events ->
                        assertEquals(1, events.size)
                        events.forEach { event ->
                            event as ActivityStreamEntry.Tracked
                            assertEquals(1, event.files.size)
                            assertEquals(update.fileId, event.files.first())
                        }
                        true
                    }
                )
            }
        }
    }

    @Test
    fun `stream conversion - single unused event`() {
        val (service, dao) = initStreamConversionTest()
        service.insert(Unit, ActivityEvent.Inspected("user", System.currentTimeMillis(), "fileId"))

        verify {
            dao.insertBatchIntoStream(
                any(),
                any(),
                match { events ->
                    assertEquals(0, events.size)
                    true
                }
            )
        }
    }

    @Test
    fun `stream conversion - single unused event (batch)`() {
        val (service, dao) = initStreamConversionTest()
        service.insertBatch(Unit, listOf(ActivityEvent.Inspected("user", System.currentTimeMillis(), "fileId")))

        verify {
            dao.insertBatchIntoStream(
                any(),
                any(),
                match { events ->
                    assertEquals(0, events.size)
                    true
                }
            )
        }
    }
}