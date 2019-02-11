package dk.sdu.cloud.accounting.storage.services

import dk.sdu.cloud.accounting.api.BillableItem
import dk.sdu.cloud.accounting.api.ContextQuery
import dk.sdu.cloud.accounting.api.Currencies
import dk.sdu.cloud.accounting.api.SerializedMoney
import dk.sdu.cloud.accounting.storage.Configuration
import dk.sdu.cloud.accounting.storage.api.StorageUsedEvent
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.indexing.api.AllOf
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.NumericStatisticsRequest
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.optionallyCausedBy
import dk.sdu.cloud.service.orThrow
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.Logger
import java.math.BigDecimal
import kotlin.math.roundToLong

val FIRST_PAGE = NormalizedPaginationRequest(null, null)

class StorageAccountingService<DBSession>(
    private val serviceCloud: AuthenticatedCloud,
    private val db: DBSessionFactory<DBSession>,
    private val dao: StorageAccountingDao<DBSession>,
    config: Configuration
) {
    private val pricePerUnit = BigDecimal(config.pricePerByte)
    private val currencyName = Currencies.DKK

    suspend fun calculateUsage(
        directory: String,
        owner: String,
        causedById: String? = null
    ): List<BillableItem> {
        val result = QueryDescriptions.statistics.call(
            StatisticsRequest(
                query = FileQuery(
                    listOf(directory),
                    owner = AllOf.with(owner)
                ),
                size = NumericStatisticsRequest(
                    calculateSum = true
                )
            ),
            serviceCloud.optionallyCausedBy(causedById)
        ).orThrow()

        val usedStorage = result.size?.sum?.roundToLong() ?: run {
            log.warn("Could not retrieve sum from file index!")
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }

        return listOf(
            BillableItem("Storage Used", usedStorage, SerializedMoney(pricePerUnit, currencyName))
        )
    }

    suspend fun collectCurrentStorageUsage() {
        db.withTransaction { session ->
            val id = UserDescriptions.openUserIterator.call(Unit, serviceCloud).orThrow()
            var userlist = UserDescriptions.fetchNextIterator.call(id, serviceCloud).orThrow()
            while (userlist.isNotEmpty()) {
                coroutineScope {
                    userlist.chunked(16).forEach { chunk ->
                        chunk.map {
                            async {
                                val home =
                                    FileDescriptions.findHomeFolder.call(FindHomeFolderRequest(it.id), serviceCloud)
                                        .orThrow()
                                val usage = calculateUsage(home.path, it.id).first().units
                                dao.insert(session, it, usage)
                            }
                        }.awaitAll()
                    }
                }
                userlist = UserDescriptions.fetchNextIterator.call(id, serviceCloud).orThrow()
            }
            UserDescriptions.closeIterator.call(id, serviceCloud).orThrow()
        }
    }

    fun listEventsPage(
        paging: NormalizedPaginationRequest,
        context: ContextQuery,
        user: String
    ): Page<StorageUsedEvent> {
        return db.withTransaction {
            dao.findAllPage(it, paging, context, user)
        }
    }

    fun listEvents(
        context: ContextQuery,
        user: String
    ): List<StorageUsedEvent> {
        val returnList = db.withTransaction {
            dao.findAllList(it, context, user)
        }
        if (returnList.isEmpty()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return returnList
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}

interface StorageAccountingDao<Session> {
    fun insert(
        session: Session,
        user: Principal,
        usage: Long
    )

    fun findAllByUserId(
        session: Session,
        user: String,
        paginationRequest: NormalizedPaginationRequest = FIRST_PAGE
    ): Page<StorageUsedEvent>

    fun findAllPage(
        session: Session,
        paging: NormalizedPaginationRequest,
        context: ContextQuery,
        user: String
    ): Page<StorageUsedEvent>

    fun findAllList(
        session: Session,
        context: ContextQuery,
        user: String
    ): List<StorageUsedEvent>
}
