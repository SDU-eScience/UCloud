package dk.sdu.cloud.auth.testUtil

import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.runBlocking

fun dbTruncate(db: DBContext) {
    runBlocking {
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    TRUNCATE cursor_state, 
                        login_attempts,
                        login_cooldown,
                        ott_black_list,
                        principals,
                        refresh_tokens,
                        two_factor_challenges,
                        two_factor_credentials
                """.trimIndent()
            )
        }
    }
}
