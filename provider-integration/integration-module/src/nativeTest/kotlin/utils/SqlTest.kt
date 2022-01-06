package dk.sdu.cloud.utils

import dk.sdu.cloud.plugins.compute.slurm.bindTableUpload
import dk.sdu.cloud.plugins.compute.slurm.safeSqlTableUpload
import dk.sdu.cloud.sql.Sqlite3Driver
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test

class SqlTest {
    @Test
    fun testTableUpload() {
        val dbConnection = Sqlite3Driver("db_${Random.nextULong()}").openSession()
        dbConnection.withSession { session ->
            session.prepareStatement("create table foo(id int primary key, state text not null);\n")
                .useAndInvokeAndDiscard()
            session.prepareStatement("insert into foo (id, state) values (1, 'IN_QUEUE');").useAndInvokeAndDiscard()
            session.prepareStatement("insert into foo (id, state) values (2, 'IN_QUEUE');").useAndInvokeAndDiscard()
            session.prepareStatement("insert into foo (id, state) values (3, 'IN_QUEUE');").useAndInvokeAndDiscard()
            session.prepareStatement("insert into foo (id, state) values (4, 'IN_QUEUE');").useAndInvokeAndDiscard()
            session.prepareStatement("insert into foo (id, state) values (5, 'IN_QUEUE');").useAndInvokeAndDiscard()

            val table = listOf(
                mapOf("key" to 1, "new_state" to "RUNNING"),
                mapOf("key" to 2, "new_state" to "FAILURE"),
                mapOf("key" to 4, "new_state" to "SUCCESS"),
                mapOf("key" to 5, "new_state" to "RUNNING"),
            )

            session.prepareStatement(
                """
                    with update_table as (
                        ${safeSqlTableUpload("update", table)}
                    )
                    update foo as f
                    set state = new_state
                    from update_table as u
                    where
                        u.key = f.id;
                 """.also { println(it) }
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindTableUpload("update", table)
                }
            )

            session.prepareStatement("select id, state from foo;").useAndInvoke { row ->
                println("${row.getInt(0)!!} ${row.getString(1)!!}")
            }
        }
    }
}
