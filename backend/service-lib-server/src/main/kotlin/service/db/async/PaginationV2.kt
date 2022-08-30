package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.Actor
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*

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
    @Suppress("DEPRECATION")
    if (request.consistency == PaginationRequestV2Consistency.REQUIRE) {
        throw RPCException("Unsupported. Please use PREFER instead", HttpStatusCode.BadRequest)
    }

    val ctx = this
    val next = request.next
    if (next == null) {
        return ctx.withSession { session ->
            try {
                create(session)
                val itemsToSkip = request.itemsToSkip
                if (itemsToSkip != null) {
                    skip(session, itemsToSkip)
                }

                val id = ""
                val items = fetch(session, request.itemsPerPage)

                if (items.isEmpty()) {
                    PageV2(request.itemsPerPage, emptyList(), null)
                } else {
                    val nextToken =
                        if (items.size < request.itemsPerPage) null
                        else "${(request.itemsToSkip ?: 0) + items.size}-$id"

                    PageV2(request.itemsPerPage, items, nextToken)
                }
            } catch (ex: Throwable) {
                throw ex
            }
        }
    } else {
        val parsedOffset = try {
            next.substringBefore('-').toInt() + (request.itemsToSkip ?: 0L)
        } catch (ex: Throwable) {
            throw RPCException("Invalid next token supplied", HttpStatusCode.BadRequest)
        }

        return paginateV2(principal, request.copy(itemsToSkip = parsedOffset, next = null), create, skip, fetch)
    }
}

inline fun <T, R> PageV2<T>.mapItems(mapper: (T) -> R): PageV2<R> {
    return PageV2(
        itemsPerPage,
        items.map(mapper),
        next
    )
}

inline fun <T, R> PageV2<T>.mapItemsNotNull(mapper: (T) -> R?): PageV2<R> {
    return PageV2(
        itemsPerPage,
        items.mapNotNull(mapper),
        next
    )
}
