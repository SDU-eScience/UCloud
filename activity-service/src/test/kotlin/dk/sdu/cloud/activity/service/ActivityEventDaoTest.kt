package dk.sdu.cloud.activity.service

import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.readValues
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.services.HibernateActivityEventDao
import dk.sdu.cloud.activity.util.deletedEvent
import dk.sdu.cloud.activity.util.downloadEvent
import dk.sdu.cloud.activity.util.favoriteEvent
import dk.sdu.cloud.activity.util.inspectedEvent
import dk.sdu.cloud.activity.util.movedEvent
import dk.sdu.cloud.activity.util.updatedEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.withDatabase
import org.junit.Test
import kotlin.test.assertEquals

class ActivityEventDaoTest{

    @Test
    fun `insert Test and find by user`() {
        withDatabase { db ->
            val dao = HibernateActivityEventDao()
            val results = db.withTransaction {
                dao.insert(it, downloadEvent)
                dao.insert(it, updatedEvent)
                dao.findByUser(
                    it,
                    NormalizedPaginationRequest(10,0),
                    TestUsers.user.username
                )
            }

            assertEquals(2, results.itemsInTotal)
            assertEquals(downloadEvent, results.items.first())
            assertEquals(updatedEvent, results.items.last())
        }
    }

    @Test
    fun `insert batch and find by user Test`() {
        withDatabase { db ->
            val dao = HibernateActivityEventDao()
            val results = db.withTransaction {
                dao.insertBatch(it, listOf(favoriteEvent, inspectedEvent))
                dao.findByUser(
                    it,
                    NormalizedPaginationRequest(10,0),
                    TestUsers.user.username
                )
            }

            assertEquals(2, results.itemsInTotal)
            assertEquals(inspectedEvent, results.items.first())
            assertEquals(favoriteEvent, results.items.last())
        }
    }

    @Test
    fun `insert and find by fileID Test`() {
        withDatabase { db ->
            val dao = HibernateActivityEventDao()
            var results = db.withTransaction {
                dao.insertBatch(it, listOf(movedEvent, deletedEvent))
                dao.findByFileId(
                    it,
                    NormalizedPaginationRequest(10,0),
                    "5"
                )
            }

            assertEquals(1, results.itemsInTotal)
            assertEquals(movedEvent, results.items.first())

            results = db.withTransaction {
                dao.findByFileId(
                    it,
                    NormalizedPaginationRequest(10,0),
                    "6"
                )
            }

            assertEquals(1, results.itemsInTotal)
            assertEquals(deletedEvent, results.items.first())
        }
    }
}
