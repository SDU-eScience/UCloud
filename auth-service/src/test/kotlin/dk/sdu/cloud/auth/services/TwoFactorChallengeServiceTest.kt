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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test(expected = TwoFactorException.InvalidChallenge::class)
    fun `test verification - no challenge`() {
        with(initTest<Any>()) {
            every { twoFactorDAO.findActiveChallengeOrNull(any(), any()) } returns null

            service.verifyChallenge("123456", 0)
        }
    }

    @Test
    fun `test verification - with challenge`() {
        val totpService = mockk<TOTPService>(relaxed = true)
        with(initTest<Any>(totpService = totpService)) {
            val user = userDAO.withUser()
            val secret = "secret"
            val code = 42

            every { twoFactorDAO.findActiveChallengeOrNull(any(), any()) } returns
                    TwoFactorChallenge.Login(
                        "123456",
                        System.currentTimeMillis() + 10_000,
                        TwoFactorCredentials(user, secret, true, 42),
                        "local-dev"
                    )

            service.verifyChallenge("123456", code)

            verify {
                totpService.verify(secret, code)
            }
        }
    }

    @Test
    fun `test upgrading credentials - happy path`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            val credentials = TwoFactorCredentials(user, "secret", false, 42)
            service.upgradeCredentials(credentials)

            verify {
                twoFactorDAO.createCredentials(any(), match {
                    assertEquals(credentials.sharedSecret, it.sharedSecret)
                    assertEquals(true, it.enforced)
                    assertEquals(null, it.id)
                    assertEquals(credentials.principal, it.principal)
                    true
                })
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test upgrading credentials - already enforced`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            val credentials = TwoFactorCredentials(user, "secret", true, 42)
            service.upgradeCredentials(credentials)
        }
    }

    @Test
    fun `test creating login challenge - no enforced`() {
        with(initTest<Any>()) {
            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), any()) } returns null

            assertNull(service.createLoginChallengeOrNull("user", "service"))
        }
    }

    @Test
    fun `test creating login challenge`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), any()) } returns TwoFactorCredentials(
                user,
                "secret",
                true,
                42
            )

            assertNotNull(service.createLoginChallengeOrNull(user.id, "service"))

            verify {
                twoFactorDAO.createChallenge(any(), any())
            }
        }
    }

    @Test
    fun `test is connected`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), any()) } returns TwoFactorCredentials(
                user,
                "secret",
                true,
                42
            )

            assertTrue(service.isConnected(user.id))
        }
    }

    @Test
    fun `test is not connected`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), any()) } returns null

            assertFalse(service.isConnected(user.id))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test constraints on challenges - login - not happy`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            TwoFactorChallenge.Login(
                "id",
                System.currentTimeMillis(),
                TwoFactorCredentials(user, "secret", false, 42),
                "service"
            )
        }
    }

    @Test
    fun `test constraints on challenges - login - happy`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            TwoFactorChallenge.Login(
                "id",
                System.currentTimeMillis(),
                TwoFactorCredentials(user, "secret", true, 42),
                "service"
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test constraints on challenges - setup - not happy`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            TwoFactorChallenge.Setup(
                "id",
                System.currentTimeMillis(),
                TwoFactorCredentials(user, "secret", true, 42)
            )
        }
    }

    @Test
    fun `test constraints on challenges - setup - happy`() {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            TwoFactorChallenge.Setup(
                "id",
                System.currentTimeMillis(),
                TwoFactorCredentials(user, "secret", false, 42)
            )
        }
    }
}