package dk.sdu.cloud.accounting.storage.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.accounting.api.UsageResponse
import dk.sdu.cloud.accounting.storage.Configuration
import dk.sdu.cloud.accounting.storage.api.StorageUsedEvent
import dk.sdu.cloud.accounting.storage.services.StorageAccountingHibernateDao
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.accounting.storage.services.StorageForUserEntity
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.indexing.api.NumericStatistics
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withDatabase
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue


private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
    micro.install(HibernateFeature)
    val storageAccountingDao = StorageAccountingHibernateDao()
    val storageAccountingService = StorageAccountingService(
        micro.authenticatedCloud,
        micro.hibernateDatabase,
        storageAccountingDao,
        Configuration("0.1")
    )

    withDatabase { db ->
        db.withTransaction { session ->
            for (i in 0..50) {
                session.save(StorageForUserEntity(
                    TestUsers.user.username,
                    Date(),
                    12345
                ))
            }
        }
    }

    configureComputeTimeServer(storageAccountingService)
}

private fun KtorApplicationTestSetupContext.configureComputeTimeServer(
    storageAccountingService: StorageAccountingService<HibernateSession>
): List<Controller> {
    return listOf(
        StorageUsedController(storageAccountingService, micro.authenticatedCloud),
        StorageAccountingController(storageAccountingService, micro.authenticatedCloud)
    )
}

class StorageUsedTest{

    @Test
    fun `list events`() {
        withKtorTest(
            setup,
            test= {

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/accounting/storage/bytesUsed/events",
                    user = TestUsers.user,
                    params = mapOf( "since" to "12345")
                )

                request.assertSuccess()
                val response = defaultMapper.readValue<Page<StorageUsedEvent>>(request.response.content!!)

                assertEquals(51, response.itemsInTotal)
                assertEquals(6, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals(12345, response.items.first().bytesUsed)
            }
        )
    }

    @Test
    fun `list events no params`() {
        withKtorTest(
            setup,
            test= {

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/accounting/storage/bytesUsed/events",
                    user = TestUsers.user
                )

                request.assertSuccess()
                val response = defaultMapper.readValue<Page<StorageUsedEvent>>(request.response.content!!)

                assertEquals(51, response.itemsInTotal)
                assertEquals(6, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals(12345, response.items.first().bytesUsed)            }
        )
    }

    @Test
    fun `list events no events in time slot`() {
        withKtorTest(
            setup,
            test= {

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/accounting/storage/bytesUsed/events",
                    user = TestUsers.user,
                    params = mapOf("since" to "1044173471000", "until" to "1075709471000")
                    // since 2003-02-02 to 2004-02-02
                )

                request.assertSuccess()
                val response = defaultMapper.readValue<Page<StorageUsedEvent>>(request.response.content!!)

                assertEquals(0, response.itemsInTotal)
                assertEquals(0, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
            }
        )
    }

    @Test
    fun `chart test`() {
        withKtorTest(
            setup,
            test= {
                CloudMock.mockCallSuccess(
                    FileDescriptions,
                    { FileDescriptions.findHomeFolder},
                    FindHomeFolderResponse("/home/user")
                )

                CloudMock.mockCallSuccess(
                    QueryDescriptions,
                    { QueryDescriptions.statistics },
                    StatisticsResponse(
                        22,
                        NumericStatistics(null, null, null, 150.4, emptyList()),
                        NumericStatistics(null, null, null, null, emptyList())
                    )
                )

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/accounting/storage/bytesUsed/chart",
                    user = TestUsers.user,
                    params = mapOf( "since" to "12345")
                )
                request.assertSuccess()

                println(request.response.content)
                //TODO Works but not pretty
                assertTrue(request.response.content?.contains("\"dataTypes\":[\"datetime\",\"bytes\"],\"dataTitle\":\"Storage Used\"")!!)

            }
        )
    }

    @Test
    fun `Test Usage - no params`() {
        withKtorTest(
            setup,
            test = {
                with(engine) {
                    CloudMock.mockCallSuccess(
                        QueryDescriptions,
                        { QueryDescriptions.statistics },
                        StatisticsResponse(
                            22,
                            NumericStatistics(null, null, null, 150.4, emptyList()),
                            NumericStatistics(null, null, null, null, emptyList())
                        )
                    )

                    CloudMock.mockCallSuccess(
                        FileDescriptions,
                        {FileDescriptions.findHomeFolder},
                        FindHomeFolderResponse("/home/user")
                    )

                    run {
                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/storage/bytesUsed/usage",
                            user = TestUsers.user
                        )
                        request.assertSuccess()

                        val items = defaultMapper.readValue<UsageResponse>(request.response.content!!)
                        assertEquals(150, items.usage)
                    }
                }
            }
        )
    }

    @Test
    fun `Test Usage - since and until `() {
        withKtorTest(
            setup,
            test = {
                with(engine) {
                    withDatabase { db ->
                        db.withTransaction { session ->
                            session.save(
                                StorageForUserEntity(
                                    TestUsers.user.username,
                                    Date(946800671000), //2000-01-02
                                    11
                                )
                            )
                            session.save(
                                StorageForUserEntity(
                                    TestUsers.user.username,
                                    Date(978423071000), //2001-01-02
                                    1
                                )
                            )
                        }
                    }

                    CloudMock.mockCallSuccess(
                        FileDescriptions,
                        {FileDescriptions.findHomeFolder},
                        FindHomeFolderResponse("/home/user")
                    )
                    run {

                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/storage/bytesUsed/usage",
                            user = TestUsers.user,
                            params = mapOf("since" to "941443871000", "until" to "949479071000")
                            // since 1999-11-02 until 2000-02-02
                        )
                        request.assertSuccess()

                        val items = defaultMapper.readValue<UsageResponse>(request.response.content!!)
                        assertEquals(11, items.usage)
                    }

                    run {
                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/storage/bytesUsed/usage",
                            user = TestUsers.user,
                            params = mapOf("since" to "941443871000", "until" to "981101471000")
                            // since 1999-11-02 until 2001-02-02
                        )
                        request.assertSuccess()

                        val items = defaultMapper.readValue<UsageResponse>(request.response.content!!)
                        assertEquals(1, items.usage)
                    }

                    //No entries in time slot
                    run {
                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/storage/bytesUsed/usage",
                            user = TestUsers.user,
                            params = mapOf("since" to "1012637471000", "until" to "1044173471000")
                            // since 2002-02-02 until 2003-02-02
                        )
                        request.assertStatus(HttpStatusCode.NotFound)
                    }
                }
            }
        )
    }

    @Test
    fun `Test Usage - no since but until `() {
        withKtorTest(
            setup,
            test = {
                with(engine) {
                    withDatabase { db ->
                        db.withTransaction { session ->
                            session.save(
                                StorageForUserEntity(
                                    TestUsers.user.username,
                                    Date(946800671000), //2000-01-02
                                    11
                                )
                            )
                            session.save(
                                StorageForUserEntity(
                                    TestUsers.user.username,
                                    Date(978423071000), //2001-01-02
                                    1
                                )
                            )
                        }
                    }

                    CloudMock.mockCallSuccess(
                        FileDescriptions,
                        {FileDescriptions.findHomeFolder},
                        FindHomeFolderResponse("/home/user")
                    )

                    run {
                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/storage/bytesUsed/usage",
                            user = TestUsers.user,
                            params = mapOf("until" to "949479071000")
                            //until 2000-02-02
                        )
                        request.assertSuccess()

                        val items = defaultMapper.readValue<UsageResponse>(request.response.content!!)
                        assertEquals(11, items.usage)
                    }

                    run {

                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/storage/bytesUsed/usage",
                            user = TestUsers.user,
                            params = mapOf("until" to "981101471000")
                            //until 2001-02-02
                        )
                        request.assertSuccess()

                        val items = defaultMapper.readValue<UsageResponse>(request.response.content!!)
                        assertEquals(1, items.usage)
                    }

                    run {

                        CloudMock.mockCallSuccess(
                            QueryDescriptions,
                            { QueryDescriptions.statistics },
                            StatisticsResponse(
                                22,
                                NumericStatistics(null, null, null, 150.4, emptyList()),
                                NumericStatistics(null, null, null, null, emptyList())
                            )
                        )

                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/storage/bytesUsed/usage",
                            user = TestUsers.user,
                            params = mapOf("until" to Date().time)
                            //until now
                        )
                        request.assertSuccess()

                        val items = defaultMapper.readValue<UsageResponse>(request.response.content!!)
                        assertEquals(150, items.usage)
                    }
                }
            }
        )
    }
}
