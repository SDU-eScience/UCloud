package dk.sdu.cloud.share.services.db

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

suspend fun AsyncDBConnection.paginatedQuery(
    table: String,
    pagination: NormalizedPaginationRequest,
    block: EnhancedPreparedStatement.() -> Unit,
    where: String
): Page<RowData> {
    require(table.matches(columnRegex))

    val itemsInTotal = sendPreparedStatement(
        block,
        "select count(*) from $table where $where"
    ).rows.singleOrNull()?.getLong(0) ?: 0

    val items = sendPreparedStatement(
        {
            block()
            setParameter("limit", pagination.itemsPerPage)
            setParameter("offset", pagination.itemsPerPage * pagination.page)
        },

        "select * from $table where $where limit ?limit offset ?offset"
    ).rows

    return Page(itemsInTotal.toInt(), pagination.itemsPerPage, pagination.page, items)
}
