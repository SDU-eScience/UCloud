package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.service.db.DBSessionFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import org.junit.Test

class TwoFactorChallengeServiceTest {
    private fun UserDAO<*>.withUser(
        principal: Principal = Person.ByPassword(
            "user",
            Role.USER,
            null,
            "user",
            "user",
            null,
            null,
            emptyList(),
            null,
            ByteArray(64),
            ByteArray(64)
        )
    ): Principal {
        @Suppress("UNCHECKED_CAST")
        val dao = this as UserDAO<Any>
        every { dao.findById(any(), principal.id) } returns principal
        every { dao.findByIdOrNull(any(), principal.id) } returns principal
        return principal
    }

    private data class TestContext<DBSession>(
        val totpService: TOTPService,
        val qrService: QRService,
        val twoFactorDAO: TwoFactorDAO<DBSession>,
        val userDAO: UserDAO<DBSession>,
        val db: DBSessionFactory<DBSession>,
        val service: TwoFactorChallengeService<DBSession>
    )

    private fun <DBSession> initTest(
        totpService: TOTPService = WSTOTPService(),
        qrService: QRService = ZXingQRService(),
        twoFactorDAO: TwoFactorDAO<DBSession> = mockk(relaxed = true),
        userDAO: UserDAO<DBSession> = mockk(relaxed = true),
        db: DBSessionFactory<DBSession> = mockk(relaxed = true)
    ): TestContext<DBSession> {
        return TestContext(
            totpService,
            qrService,
            twoFactorDAO,
            userDAO,
            db,
            TwoFactorChallengeService(db, twoFactorDAO, userDAO, totpService, qrService)
        )
    }

    @Test
    fun `test creating credentials for valid user`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()

            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), user.id) } returns null

            val result = service.createSetupCredentialsAndChallenge(user.id)
            verify {
                twoFactorDAO.createChallenge(any(), match {
                    assertEquals(it.challengeId, result.challengeId)
                    true
                })

                twoFactorDAO.createCredentials(any(), any())
            }
        }
    }

    @Test(expected = TwoFactorException.InvalidPrincipalType::class)
    fun `test creating credentials for service`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser(ServicePrincipal("_foobar", Role.SERVICE))

            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), user.id) } returns null

            service.createSetupCredentialsAndChallenge(user.id)
        }
    }

    @Test(expected = TwoFactorException.AlreadyBound::class)
    fun `test creating credentials when already bound`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()

            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), user.id) } returns TwoFactorCredentials(
                user,
                "foo",
                true,
                42
            )

            service.createSetupCredentialsAndChallenge(user.id)
        }
    }

    @Test(expected = TwoFactorException.InternalError::class)
    fun `test creating credentials when user does not exist`() {
        with(initTest<Any>()) {
            every { userDAO.findByIdOrNull(any(), any()) } returns null
            every { userDAO.findById(any(), any()) } throws UserException.NotFound()

            service.createSetupCredentialsAndChallenge("doesNotExist")
        }
    }
}