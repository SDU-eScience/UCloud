package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
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
            password = ByteArray(64),
            salt = ByteArray(64),
            twoFactorAuthentication = false,
            serviceLicenseAgreement = 0
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
    fun `test creating credentials for valid user`(): Unit = runBlocking {
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
    fun `test creating credentials for service`(): Unit = runBlocking {
        with(initTest<Any>()) {
            val user = userDAO.withUser(ServicePrincipal("_foobar", Role.SERVICE))

            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), user.id) } returns null

            service.createSetupCredentialsAndChallenge(user.id)
            Unit
        }
    }

    @Test(expected = TwoFactorException.AlreadyBound::class)
    fun `test creating credentials when already bound`(): Unit = runBlocking {
        with(initTest<Any>()) {
            val user = userDAO.withUser()

            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), user.id) } returns TwoFactorCredentials(
                user,
                "foo",
                true,
                42
            )

            service.createSetupCredentialsAndChallenge(user.id)
            Unit
        }
    }

    @Test(expected = TwoFactorException.InternalError::class)
    fun `test creating credentials when user does not exist`(): Unit = runBlocking {
        with(initTest<Any>()) {
            every { userDAO.findByIdOrNull(any(), any()) } returns null
            every { userDAO.findById(any(), any()) } throws UserException.NotFound()

            service.createSetupCredentialsAndChallenge("doesNotExist")
            Unit
        }
    }

    @Test(expected = TwoFactorException.InvalidChallenge::class)
    fun `test verification - no challenge`(): Unit = runBlocking {
        with(initTest<Any>()) {
            every { twoFactorDAO.findActiveChallengeOrNull(any(), any()) } returns null

            service.verifyChallenge("123456", 0)
            Unit
        }
    }

    @Test
    fun `test verification - with challenge`(): Unit = runBlocking {
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
    fun `test upgrading credentials - happy path`(): Unit = runBlocking {
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

    @Test
    fun `test 2fa status for password users`() = runBlocking {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        val twoFactorDAO = TwoFactorHibernateDAO()
        val userDAO = UserHibernateDAO(PasswordHashingService(), twoFactorDAO)
        val db = micro.hibernateDatabase
        val service = TwoFactorChallengeService(db, twoFactorDAO, userDAO, WSTOTPService(), ZXingQRService())

        val user = Person.ByPassword(
            "user",
            Role.USER,
            null,
            "user",
            "user",
            null,
            null,
            password = ByteArray(64),
            salt = ByteArray(64),
            twoFactorAuthentication = false,
            serviceLicenseAgreement = 0
        )

        db.withTransaction { session ->
            userDAO.insert(
                session, user
            )
        }

        assertFalse(user.twoFactorAuthentication)

        db.withTransaction { session ->
            assertFalse((userDAO.findById(session, user.id) as Person.ByPassword).twoFactorAuthentication)
        }

        val setup = service.createSetupCredentialsAndChallenge(user.id)

        db.withTransaction { session ->
            assertFalse((userDAO.findById(session, user.id) as Person.ByPassword).twoFactorAuthentication)
        }

        val challenge = db.withTransaction { session ->
            twoFactorDAO.findActiveChallengeOrNull(session, setup.challengeId)
        }!!

        service.upgradeCredentials(challenge.credentials)

        db.withTransaction { session ->
            assertTrue((userDAO.findById(session, user.id) as Person.ByPassword).twoFactorAuthentication)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test upgrading credentials - already enforced`(): Unit = runBlocking {
        with(initTest<Any>()) {
            val user = userDAO.withUser()
            val credentials = TwoFactorCredentials(user, "secret", true, 42)
            service.upgradeCredentials(credentials)
        }
    }

    @Test
    fun `test creating login challenge - no enforced`(): Unit = runBlocking {
        with(initTest<Any>()) {
            every { twoFactorDAO.findEnforcedCredentialsOrNull(any(), any()) } returns null

            assertNull(service.createLoginChallengeOrNull("user", "service"))
        }
    }

    @Test
    fun `test creating login challenge`(): Unit = runBlocking {
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
    fun `test is connected`(): Unit = runBlocking {
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
    fun `test is not connected`(): Unit = runBlocking {
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
