package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.integration.IntegrationTest

class PaginationTest : IntegrationTest() {
    override fun defineTests() {

    }
    /*
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
            TestUsers.admin.toActor(),
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
     */
}
