package dk.sdu.cloud.news.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.news.api.NewsPost
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import org.joda.time.LocalDateTime
import java.util.*

object NewsTable : SQLTable("news") {
    val id = long("id", notNull = true)
    val title = text("title", notNull = true)
    val subtitle = text("subtitle", notNull = true)
    val body = text("body", notNull = true)
    val postedBy = text("posted_by", notNull = true)
    val showFrom = timestamp("show_from", notNull = true)
    val hideFrom = timestamp("hide_from", notNull = false)
    val hidden = bool("hidden", notNull = true)
    val category = text("category", notNull = true)
}

class NewsService {
    suspend fun createNewsPost(
        ctx: DBContext,
        postedBy: String,
        title: String,
        subtitle: String,
        body: String,
        showFrom: Date,
        hideFrom: Date?,
        category: String
    ) {
        ctx.withSession { session ->
            session.insert(NewsTable) {
                set(NewsTable.id, session.allocateId("id_sequence"))
                set(NewsTable.postedBy, postedBy)
                set(NewsTable.title, title)
                set(NewsTable.subtitle, subtitle)
                set(NewsTable.body, body)
                set(NewsTable.showFrom, LocalDateTime(showFrom))
                set(NewsTable.hideFrom, LocalDateTime(hideFrom))
                set(NewsTable.hidden, false)
                set(NewsTable.category, category.toLowerCase())
            }
        }
    }

    suspend fun listNewsPosts(
        ctx: DBContext,
        pagination: NormalizedPaginationRequest,
        categoryFilter: String?,
        withHidden: Boolean
    ): Page<NewsPost> {
        return ctx.withSession { session ->
            val items = session.sendPreparedStatement({
                setParameter("categoryFilter", categoryFilter)
                setParameter("offset", pagination.page * pagination.itemsPerPage)
                setParameter("limit", pagination.itemsPerPage)
                setParameter("withHidden", withHidden)
            },
            //language=sql
            """
                select
                    n.id
                    n.title
                    n.subtitle
                    n.body
                    n.posted_by
                    n.show_from
                    n.hide_from
                    n.hidden
                    n.category
                from news n
                where (?categoryFilter::text is null or n.category = ?categoryFilter) and 
                      (?withHidden = true or n.hidden = false)
                order by n.showFrom desc
                offset ?offset
                limit ?limit
            """.trimIndent()
            )
            .rows
            .map {row -> toNewsPost(row)}

            Page(items.size, pagination.itemsPerPage, pagination.page, items)
        }
    }

    suspend fun listCategories(
        ctx: DBContext
    ): List<String> {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                //language=sql
                """
                    select distinct news.category
                    from news
                """.trimIndent()
            )
            .rows.map { rowData -> rowData.getString(0)!!}
        }
    }

    suspend fun togglePostHidden(ctx: DBContext, id: Long) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                //language=sql
                """
                    UPDATE news 
                    SET hidden = NOT hidden
                    WHERE ?id = id
                """.trimIndent()
            )
        }
    }
}

fun toNewsPost(row: RowData): NewsPost {
    return NewsPost(
        id = row.getLong(0)!!,
        title = row.getString(1)!!,
        subtitle = row.getString(2)!!,
        body = row.getString(3)!!,
        postedBy = row.getString(4)!!,
        showFrom = Date(row.getLong(5)!!),
        hideFrom = row.getLong(6)?.let { Date(it) },
        hidden = row.getBoolean(7)!!,
        category = row.getString(8)!!
    )
}