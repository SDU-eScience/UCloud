package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

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
    pagination: NormalizedPaginationRequest,
    block: EnhancedPreparedStatement.() -> Unit,
    query: String
): Page<RowData> {
    val itemsInTotal = sendPreparedStatement(
        block,
        "select count(*) $query"
    ).rows.singleOrNull()?.getLong(0) ?: 0

    val items = sendPreparedStatement(
        {
            block()
            setParameter("limit", pagination.itemsPerPage)
            setParameter("offset", pagination.itemsPerPage * pagination.page)
        },

        "select * $query limit ?limit offset ?offset"
    ).rows

    return Page(itemsInTotal.toInt(), pagination.itemsPerPage, pagination.page, items)
}
