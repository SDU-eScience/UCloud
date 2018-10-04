package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.api.*
import dk.sdu.cloud.activity.services.ActivityStream
import dk.sdu.cloud.activity.services.ActivityStreamSubject
import dk.sdu.cloud.activity.services.HibernateActivityStreamDao
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HibernateActivityStreamDaoTest {
    private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
        HibernateSessionFactory.create(H2_TEST_CONFIG.copy(showSQLInStdout = true)).use(closure)
    }

    private data class TestContext(val dao: HibernateActivityStreamDao)

    private fun initializeTest(): TestContext {
        return TestContext(HibernateActivityStreamDao())
    }

    @Test
    fun `create stream - user`() {
        val (dao) = initializeTest()
        withDatabase { db ->
            db.withTransaction {
                dao.createStreamIfNotExists(it, ActivityStream(ActivityStreamSubject.User("user")))
            }
        }
    }

    @Test
    fun `create stream - file`() {
        val (dao) = initializeTest()
        withDatabase { db ->
            db.withTransaction {
                dao.createStreamIfNotExists(it, ActivityStream(ActivityStreamSubject.File("fileId")))
            }
        }
    }

    private fun TestContext.insertIntoStream(
        db: HibernateSessionFactory,
        stream: ActivityStream,
        entry: ActivityStreamEntry<*>
    ) {
        db.withTransaction {
            dao.insertIntoStream(
                it,
                stream,
                entry
            )
        }
    }

    private fun TestContext.verifyInStream(
        db: HibernateSessionFactory,
        stream: ActivityStream,
        entry: ActivityStreamEntry<*>,
        pagination: PaginationRequest = PaginationRequest()
    ): Page<ActivityStreamEntry<*>> {
        val activityStream = db.withTransaction {
            dao.loadStream(it, stream, pagination.normalize())
        }

        assertTrue(entry in activityStream.items)
        return activityStream
    }

    private val trackedEvent = ActivityStreamEntry.Tracked(
        TrackedFileActivityOperation.MOVED,
        System.currentTimeMillis(),
        setOf(StreamFileReference.Basic("fileId", null)),
        setOf(UserReference("foo"))
    )

    private val countedEvent = ActivityStreamEntry.Counted(
        CountedFileActivityOperation.DOWNLOAD,
        System.currentTimeMillis(),
        setOf(
            StreamFileReference.WithOpCount(
                "fileId",
                null,
                42
            )
        ),
        setOf(UserReference("foo"))
    )

    @Test
    fun `insert into stream - user stream - tracked`() {
        val ctx = initializeTest()
        withDatabase { db ->
            val stream = ActivityStream(ActivityStreamSubject.User("user"))
            val entry = trackedEvent

            ctx.insertIntoStream(db, stream, entry)
            ctx.verifyInStream(db, stream, entry)
        }
    }

    @Test
    fun `insert into stream - user stream - counted`() {
        val ctx = initializeTest()
        withDatabase { db ->
            val stream = ActivityStream(ActivityStreamSubject.User("user"))
            val entry = countedEvent

            ctx.insertIntoStream(db, stream, entry)
            ctx.verifyInStream(db, stream, entry)
        }
    }

    @Test
    fun `insert into stream - file stream - tracked`() {
        val ctx = initializeTest()
        withDatabase { db ->
            val stream = ActivityStream(ActivityStreamSubject.File("fileId"))
            val entry = trackedEvent

            ctx.insertIntoStream(db, stream, entry)
            ctx.verifyInStream(db, stream, entry)
        }
    }

    @Test
    fun `insert into stream - file stream - counted`() {
        val ctx = initializeTest()
        withDatabase { db ->
            val stream = ActivityStream(ActivityStreamSubject.File("fileId"))
            val entry = countedEvent

            ctx.insertIntoStream(db, stream, entry)
            ctx.verifyInStream(db, stream, entry)
        }
    }

    private fun TestContext.insertIntoStreamWithExisting(
        db: HibernateSessionFactory,
        stream: ActivityStream,
        entry: ActivityStreamEntry<*>
    ) {
        // Insert same event twice. Should trigger the "existing entry" path
        db.withTransaction {
            dao.insertIntoStream(
                it,
                stream,
                entry
            )
        }

        db.withTransaction {
            dao.insertIntoStream(
                it,
                stream,
                entry
            )
        }
    }

    @Test
    fun `insert into stream - user stream - with existing - counted`() {
        val ctx = initializeTest()
        val stream = ActivityStream(ActivityStreamSubject.User("user"))
        val entry = countedEvent
        withDatabase { db ->
            ctx.insertIntoStreamWithExisting(db, stream, entry)

            db.withTransaction {
                val page = ctx.dao.loadStream(it, stream, PaginationRequest().normalize())
                assertEquals(1, page.items.size)
                val resultEntry = page.items.first() as ActivityStreamEntry.Counted
                assertEquals(1, countedEvent.files.size)
                assertEquals(countedEvent.files.first().id, resultEntry.files.first().id)
                assertEquals(countedEvent.files.first().count * 2, resultEntry.files.first().count)
            }
        }
    }

    @Test
    fun `insert into stream - file stream - with existing - counted`() {
        val ctx = initializeTest()
        val stream = ActivityStream(ActivityStreamSubject.User("user"))
        val entry = trackedEvent

        withDatabase { db ->
            ctx.insertIntoStreamWithExisting(db, stream, entry)

            db.withTransaction { session ->
                val page = ctx.dao.loadStream(session, stream, PaginationRequest().normalize())
                assertEquals(1, page.items.size)
                val resultEntry = page.items.first() as ActivityStreamEntry.Tracked
                assertTrue(trackedEvent.files.first() in resultEntry.files.asSequence().map { it }.toSet())
                assertEquals(1, resultEntry.files.size)
            }
        }
    }

    @Test
    fun `insert into stream - multiple files - user stream - counted`() {
        val ctx = initializeTest()
        val stream = ActivityStream(ActivityStreamSubject.User("user"))
        val fileWithTwoEvents = "fileA"
        val fileWithOneEvent = "fileB"
        val downloadCount = 10

        val entries = listOf(
            ActivityStreamEntry.Counted(
                CountedFileActivityOperation.DOWNLOAD,
                System.currentTimeMillis(),
                setOf(
                    StreamFileReference.WithOpCount(fileWithTwoEvents, null, downloadCount),
                    StreamFileReference.WithOpCount(fileWithOneEvent, null, downloadCount)
                ),
                setOf(UserReference("foo"))
            ),
            ActivityStreamEntry.Counted(
                CountedFileActivityOperation.DOWNLOAD,
                System.currentTimeMillis(),
                setOf(
                    StreamFileReference.WithOpCount(fileWithTwoEvents, null, downloadCount)
                ),
                setOf(UserReference("foo"))
            )
        )


        withDatabase { db ->
            db.withTransaction { session ->
                ctx.dao.insertBatchIntoStream(session, stream, entries)
            }

            db.withTransaction { session ->
                val page = ctx.dao.loadStream(session, stream, PaginationRequest().normalize())
                assertEquals(1, page.itemsInTotal)
                assertEquals(1, page.items.size)

                val entry = page.items.first() as ActivityStreamEntry.Counted
                val f1 = entry.files.find { it.id == fileWithOneEvent }!!
                val f2 = entry.files.find { it.id == fileWithTwoEvents }!!

                assertEquals(downloadCount, f1.count)
                assertEquals(downloadCount * 2, f2.count)
            }
        }
    }


    @Test
    fun `insert into stream - counted entry exists - new file entry`() {
        val ctx = initializeTest()
        val fileA = "fileA"
        val fileB = "fileB"
        val downloadCount = 10
        val operation = CountedFileActivityOperation.DOWNLOAD

        val entry1 = ActivityStreamEntry.Counted(
            operation,
            System.currentTimeMillis(),
            setOf(StreamFileReference.WithOpCount(fileA, null, downloadCount)),
            setOf(UserReference("foo"))
        )

        val entry2 = ActivityStreamEntry.Counted(
            operation,
            System.currentTimeMillis(),
            setOf(StreamFileReference.WithOpCount(fileB, null, downloadCount)),
            setOf(UserReference("foo"))
        )

        val stream = ActivityStream(ActivityStreamSubject.User("user"))

        withDatabase { db ->
            db.withTransaction {
                ctx.dao.insertIntoStream(it, stream, entry1)
            }

            db.withTransaction {
                ctx.dao.insertIntoStream(it, stream, entry2)
            }

            db.withTransaction { session ->
                val loadedStream = ctx.dao.loadStream(session, stream, PaginationRequest().normalize())
                assertEquals(1, loadedStream.itemsInTotal)
                assertEquals(1, loadedStream.items.size)

                val counted = loadedStream.items.single() as ActivityStreamEntry.Counted
                assertEquals(2, counted.files.size)

                val fileAEntry = counted.files.find { it.id == fileA }!!
                val fileBEntry = counted.files.find { it.id == fileB }!!

                assertEquals(fileAEntry.count, downloadCount)
                assertEquals(fileBEntry.count, downloadCount)
            }
        }
    }

}