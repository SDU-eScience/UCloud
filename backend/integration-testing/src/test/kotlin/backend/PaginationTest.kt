package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.PaginationRequestV2
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import kotlin.test.Test

class PaginationTest : IntegrationTest() {
    @Test
    fun `test simple pagination`() = t {
        with(UCloudLauncher) {
            val db = TestDB.dbSessionFactory("public")
            db.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        create table testing(
                            number int
                        );
                    """
                )

                repeat(1000) {
                    session.sendPreparedStatement(
                        {
                            setParameter("number", it)
                        },
                        """
                            insert into testing values(:number)
                        """
                    )
                }
            }

            val request = PaginationRequestV2(50, itemsToSkip = 50).normalize()
            var page = paginate(db, request)
            println(page)
            while (true) {
                if (page.next == null) break
                page = paginate(db, request.copy(next = page.next))
                println(page)
            }
        }
    }

    private suspend fun paginate(
        db: AsyncDBSessionFactory,
        request: NormalizedPaginationRequestV2,
    ): PageV2<Int?> {
        val page = db.paginateV2(
            TestUsers.admin,
            request,
            create = { session ->
                session.sendPreparedStatement(
                    {},

                    """
                        declare c cursor for
                            select * from testing order by number;
                    """
                )
            },
            mapper = { _, rows -> rows.map { it.getInt(0) } }
        )
        return page
    }
}