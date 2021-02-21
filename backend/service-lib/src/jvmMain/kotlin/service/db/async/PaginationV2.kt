package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*

internal data class OpenConnection(
    val username: String,
    val session: AsyncDBConnection,
    val db: AsyncDBSessionFactory,
)

object PaginationV2Cache {
    fun init(scope: BackgroundScope) {
        scope.launch {
            while (isActive) {
                cache.cleanup()
                delay(1000 * 10)
            }
        }
    }

    internal val cache = SimpleCache<String, OpenConnection>(
        maxAge = 1000 * 60 * 1,
        lookup = { null }, // must be inserted explicitly
        onRemove = { _, (_, v, db) ->
            db.rollback(v)
            db.closeSession(v)
        }
    )
}

data class InjectedSQLStringBeCareful(val text: String)

suspend fun <T> DBContext.paginateV2(
    principal: Actor,
    request: NormalizedPaginationRequestV2,
    create: suspend (session: AsyncDBConnection) -> Unit,
    mapper: suspend (session: AsyncDBConnection, set: ResultSet) -> List<T>,
    cursor: InjectedSQLStringBeCareful = InjectedSQLStringBeCareful("c"),
): PageV2<T> {
    return paginateV2(
        principal,
        request,
        create = create,
        skip = { session, count ->
            session.sendQuery("move forward $count from ${cursor.text}")
        },
        fetch = { session, count ->
            mapper(session, session.sendQuery("fetch forward $count from ${cursor.text}").rows)
        }
    )
}

suspend fun <T> DBContext.paginateV2(
    principal: Actor,
    request: NormalizedPaginationRequestV2,
    create: suspend (session: AsyncDBConnection) -> Unit,
    skip: suspend (session: AsyncDBConnection, count: Long) -> Unit,
    fetch: suspend (session: AsyncDBConnection, count: Int) -> List<T>,
): PageV2<T> {
    val ctx = this
    require(ctx is AsyncDBSessionFactory)
    if (request.next == null) {
        val session = ctx.openSession()
        ctx.openTransaction(session)
        var idToRemove: String? = null
        try {
            create(session)
            if (request.itemsToSkip != null) {
                skip(session, request.itemsToSkip)
            }

            val id = "${UUID.randomUUID()}"
            idToRemove = id

            PaginationV2Cache.cache.insert(id, OpenConnection(principal.safeUsername(), session, ctx))

            val items = fetch(session, request.itemsPerPage)
            return if (items.isEmpty()) {
                PaginationV2Cache.cache.remove(id)
                PageV2(request.itemsPerPage, emptyList(), null)
            } else {
                val next =
                    if (items.size < request.itemsPerPage) null
                    else "${(request.itemsToSkip ?: 0) + items.size}-$id"
                PaginationV2Cache.cache.remove(id)

                PageV2(request.itemsPerPage, items, next)
            }
        } catch (ex: Throwable) {
            if (idToRemove != null) {
                PaginationV2Cache.cache.remove(idToRemove)
            }
            throw ex
        }
    } else {
        val id = request.next.substringAfter('-')
        val cached = PaginationV2Cache.cache.get(id)

        val parsedOffset = try {
            request.next.substringBefore('-').toInt() + (request.itemsToSkip ?: 0L)
        } catch (ex: Throwable) {
            throw RPCException("Invalid next token supplied", HttpStatusCode.BadRequest)
        }

        if (cached == null) {
            if (request.consistency == PaginationRequestV2Consistency.REQUIRE) {
                throw RPCException("Outdated 'next' token supplied. Please restart query.", HttpStatusCode.Gone)
            }
            return paginateV2(principal, request.copy(itemsToSkip = parsedOffset, next = null), create, skip, fetch)
        } else {
            val (username, session) = cached
            if (username != principal.safeUsername()) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            try {
                if (request.itemsToSkip != null) skip(session, request.itemsToSkip)
                val items = fetch(session, request.itemsPerPage)
                return if (items.isEmpty()) {
                    PaginationV2Cache.cache.remove(id)
                    PageV2(request.itemsPerPage, emptyList(), null)
                } else {
                    PageV2(request.itemsPerPage, items, "${(request.itemsToSkip ?: 0) + items.size + parsedOffset}-$id")
                }
            } catch (ex: Throwable) {
                PaginationV2Cache.cache.remove(id)
                throw ex
            }
        }
    }
}

inline fun <T, R> PageV2<T>.mapItems(mapper: (T) -> R): PageV2<R> {
    return PageV2(
        itemsPerPage,
        items.map(mapper),
        next
    )
}
