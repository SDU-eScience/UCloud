package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.offset
import org.intellij.lang.annotations.Language

/**
 * Utility function for pagination queries.
 *
 * Under the hood this will count the number of records in the following manner: "select count(*) $query".
 * Similarly, we will select the appropriate records using: "select * $query limit ?limit offset ?offset".
 *
 * A prepared statement is used and additional parameters can be passed in [block]. The parameters ?limit and ?offset
 * are will be overwritten by this function if already defined.
 *
 * Example:
 *
 * ```kotlin
 * lateinit var connection: AsyncDBConnection
 *
 * // Find all dogs named "Fie"
 * connection.paginatedQuery(
 *     pagination,
 *     {
 *         setParameter("name", "Fie")
 *     },
 *     "from dogs where name = ?name"
 * )
 * ```
 */
suspend fun AsyncDBConnection.paginatedQuery(
    pagination: NormalizedPaginationRequest?,
    block: EnhancedPreparedStatement.() -> Unit,
    @Language("sql", prefix = "select count(*) ")
    query: String,
    @Language("sql", prefix = "select 1 ")
    orderBy: String? = null
): Page<RowData> {
    val itemsInTotal =
        if (pagination == null) null
        else {
            sendPreparedStatement(
                block,
                "select count(*) $query"
            ).rows.singleOrNull()?.getLong(0) ?: 0
        }

    val items = sendPreparedStatement(
        {
            block()
            setParameter("limit", pagination?.itemsPerPage ?: Int.MAX_VALUE)
            setParameter("offset", pagination?.offset ?: 0)
        },

        "select * $query ${orderBy ?: ""} limit ?limit offset ?offset"
    ).rows

    return Page.forRequest(pagination, itemsInTotal?.toInt(), items)
}
