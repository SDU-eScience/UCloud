package dk.sdu.cloud.password.reset.services

import dk.sdu.cloud.auth.api.LookupUserWithEmailResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.password.reset.api.PasswordResetServiceDescription
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import io.ktor.http.HttpStatusCode
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.security.SecureRandom
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class PasswordResetServiceTest {

    companion object {
        private lateinit var db: AsyncDBSessionFactory
        private lateinit var embDB: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDB) = TestDB.from(PasswordResetServiceDescription)
            this.db = db
            this.embDB = embDB
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking {
                db.close()
            }
            embDB.close()
        }

    }

    fun truncate() {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    """
                    TRUNCATE password_reset_requests
                """.trimIndent()
                )
            }
        }
    }

    @BeforeTest
    fun before() {
        truncate()
    }

    @AfterTest
    fun after() {
        truncate()
    }

    @Test
    fun `test Email not found`() {
        val client = ClientMock.authenticatedClient
        val resetDAO = ResetRequestsAsyncDao()
        val secureRand = SecureRandom()
        val service = PasswordResetService(db, client, resetDAO, secureRand)
        ClientMock.mockCallError(
            UserDescriptions.lookupUserWithEmail,
            null,
            HttpStatusCode.NotFound
        )

        runBlocking {
            service.createResetRequest("email@email")
            val numberOfResults = db.withSession { session ->
                session.sendPreparedStatement(
                    """
                        SELECT *
                        FROM password_reset_requests
                    """.trimIndent()
                ).rows.size
            }
            assertEquals(0, numberOfResults)
        }
    }

    @Test
    fun `create and reset test`() {
        val client = ClientMock.authenticatedClient
        val resetDAO = ResetRequestsAsyncDao()
        val secureRand = SecureRandom()
        val service = PasswordResetService(db, client, resetDAO, secureRand)
        ClientMock.mockCallSuccess(
            UserDescriptions.lookupUserWithEmail,
            LookupUserWithEmailResponse(
                TestUsers.user.username,
                TestUsers.user.firstName
            )
        )
        ClientMock.mockCallSuccess(
            MailDescriptions.send,
            Unit
        )
        ClientMock.mockCallSuccess(
            UserDescriptions.changePasswordWithReset,
            Unit
        )

        runBlocking {
            service.createResetRequest("email@email")
            val token = db.withSession {
                it.sendPreparedStatement(
                    """
                    SELECT *
                    FROM password_reset_requests
                """.trimIndent()
                ).rows.singleOrNull()?.getField(PasswordResetRequestTable.token)
            }
            service.newPassword(token!!, "newPassword")
        }
    }


    @Test(expected = RPCException::class)
    fun `create and reset late test`() {
        val client = ClientMock.authenticatedClient
        val resetDAO = ResetRequestsAsyncDao()
        val secureRand = SecureRandom()
        val service = PasswordResetService(db, client, resetDAO, secureRand)
        ClientMock.mockCallSuccess(
            UserDescriptions.lookupUserWithEmail,
            LookupUserWithEmailResponse(
                TestUsers.user.username,
                TestUsers.user.firstName
            )
        )
        ClientMock.mockCallSuccess(
            MailDescriptions.send,
            Unit
        )
        ClientMock.mockCallSuccess(
            UserDescriptions.changePasswordWithReset,
            Unit
        )

        runBlocking {
            service.createResetRequest("email@email")
            val token = db.withSession {
                val token = it.sendPreparedStatement(
                    """
                    SELECT *
                    FROM password_reset_requests
                """.trimIndent()
                ).rows.singleOrNull()?.getField(PasswordResetRequestTable.token)

                it.sendPreparedStatement(
                    {
                        setParameter("token", token)
                        setParameter(
                            "time",
                            LocalDateTime(Time.now(), DateTimeZone.UTC).minusDays(1).toDate().time / 1000
                        )
                    },
                    """
                        UPDATE password_reset_requests
                        SET expires_at = to_timestamp(?time)
                        WHERE token = ?token
                    """.trimIndent()
                )
                token
            }

            service.newPassword(token!!, "newPassword")
        }
    }
}
