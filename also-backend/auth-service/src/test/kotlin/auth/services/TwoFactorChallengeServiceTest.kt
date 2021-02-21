package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwoFactorChallengeServiceTest {
    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDB: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDB) = TestDB.from(AuthServiceDescription)
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

    @BeforeTest
    fun before() {
        dbTruncate(db)
    }

    @AfterTest
    fun after() {
        dbTruncate(db)
    }

    private fun UserAsyncDAO.withUser(
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
        val dao = this as UserAsyncDAO
        coEvery { dao.findById(any(), principal.id) } returns principal
        coEvery { dao.findByIdOrNull(any(), principal.id) } returns principal
        return principal
    }

    private data class TestContext(
        val totpService: TOTPService,
        val qrService: QRService,
        val twoFactorDAO: TwoFactorAsyncDAO,
        val userDAO: UserAsyncDAO,
        val db: AsyncDBSessionFactory,
        val service: TwoFactorChallengeService
    )

    private fun initTest(
        totpService: TOTPService = WSTOTPService(),
        qrService: QRService = ZXingQRService(),
        twoFactorDAO: TwoFactorAsyncDAO = mockk(),
        userDAO: UserAsyncDAO = mockk(relaxed = true),
        db: AsyncDBSessionFactory = mockk(relaxed = true)
    ): TestContext {
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
        with(initTest(db = db)) {
            val user = userDAO.withUser()

            coEvery { twoFactorDAO.findEnforcedCredentialsOrNull(any(), user.id) } returns null
            coEvery { twoFactorDAO.createChallenge(any(), any()) } just runs
            coEvery { twoFactorDAO.createCredentials(any(), any()) } returns 2

            val result = service.createSetupCredentialsAndChallenge(user.id)
            coVerify {
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
        with(initTest(db = db)) {
            val user = userDAO.withUser(ServicePrincipal("_foobar", Role.SERVICE))

            coEvery { twoFactorDAO.findEnforcedCredentialsOrNull(any(), user.id) } returns null

            service.createSetupCredentialsAndChallenge(user.id)
            Unit
        }
    }

    @Test(expected = TwoFactorException.AlreadyBound::class)
    fun `test creating credentials when already bound`(): Unit = runBlocking {
        with(initTest(db = db)) {
            val user = userDAO.withUser()

            coEvery { twoFactorDAO.findEnforcedCredentialsOrNull(any(), user.id) } returns TwoFactorCredentials(
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
        with(initTest(db = db)) {
            coEvery { userDAO.findByIdOrNull(any(), any()) } returns null
            coEvery { userDAO.findById(any(), any()) } throws UserException.NotFound()

            service.createSetupCredentialsAndChallenge("doesNotExist")
            Unit
        }
    }

    @Test(expected = TwoFactorException.InvalidChallenge::class)
    fun `test verification - no challenge`(): Unit = runBlocking {
        with(initTest()) {
            coEvery { twoFactorDAO.findActiveChallengeOrNull(any(), any()) } returns null

            service.verifyChallenge("123456", 0)
            Unit
        }
    }

    @Test
    fun `test verification - with challenge`(): Unit = runBlocking {
        val totpService = mockk<TOTPService>(relaxed = true)
        with(initTest(totpService = totpService)) {
            val user = userDAO.withUser()
            val secret = "secret"
            val code = 42

            coEvery { twoFactorDAO.findActiveChallengeOrNull(any(), any()) } returns
                    TwoFactorChallenge(
                        TwoFactorChallengeType.LOGIN.name,
                        "123456",
                        Time.now() + 10_000,
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
        with(initTest()) {
            coEvery { twoFactorDAO.createCredentials(any(), any()) } returns 2
            val user = userDAO.withUser()
            val credentials = TwoFactorCredentials(user, "secret", false, 42)
            service.upgradeCredentials(credentials)

            coVerify {
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
        val twoFactorDAO = TwoFactorAsyncDAO()
        val userDAO = UserAsyncDAO(PasswordHashingService(), twoFactorDAO)
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

        db.withSession { session ->
            userDAO.insert(
                session, user
            )
        }

        assertFalse(user.twoFactorAuthentication)

        db.withSession { session ->
            assertFalse((userDAO.findById(session, user.id) as Person.ByPassword).twoFactorAuthentication)
        }

        val setup = service.createSetupCredentialsAndChallenge(user.id)

        db.withSession { session ->
            assertFalse((userDAO.findById(session, user.id) as Person.ByPassword).twoFactorAuthentication)
        }

        val challenge = db.withSession { session ->
            twoFactorDAO.findActiveChallengeOrNull(session, setup.challengeId)
        }!!

        service.upgradeCredentials(challenge.credentials)

        db.withSession { session ->
            assertTrue((userDAO.findById(session, user.id) as Person.ByPassword).twoFactorAuthentication)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test upgrading credentials - already enforced`(): Unit = runBlocking {
        with(initTest()) {
            val user = userDAO.withUser()
            val credentials = TwoFactorCredentials(user, "secret", true, 42)
            service.upgradeCredentials(credentials)
        }
    }

    @Test
    fun `test creating login challenge - no enforced`(): Unit = runBlocking {
        with(initTest(db = db)) {
            coEvery { twoFactorDAO.findEnforcedCredentialsOrNull(any(), any()) } returns null

            assertNull(service.createLoginChallengeOrNull("user", "service"))
        }
    }

    @Test
    fun `test creating login challenge`(): Unit = runBlocking {
        with(initTest(db = db)) {
            val user = userDAO.withUser()
            coEvery { twoFactorDAO.findEnforcedCredentialsOrNull(any(), any()) } returns TwoFactorCredentials(
                user,
                "secret",
                true,
                42
            )
            coEvery { twoFactorDAO.createChallenge(any(), any()) } just runs
            assertNotNull(service.createLoginChallengeOrNull(user.id, "service"))

            coVerify {
                twoFactorDAO.createChallenge(any(), any())
            }
        }
    }

    @Test
    fun `test is connected`(): Unit = runBlocking {
        with(initTest()) {
            val user = userDAO.withUser()
            coEvery { twoFactorDAO.findEnforcedCredentialsOrNull(any(), any()) } returns TwoFactorCredentials(
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
        with(initTest()) {
            val user = userDAO.withUser()
            coEvery { twoFactorDAO.findEnforcedCredentialsOrNull(any(), any()) } returns null

            assertFalse(service.isConnected(user.id))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test constraints on challenges - login - not happy`() {
        with(initTest()) {
            val user = userDAO.withUser()
            TwoFactorChallenge(
                TwoFactorChallengeType.LOGIN.name,
                "id",
                Time.now(),
                TwoFactorCredentials(user, "secret", false, 42),
                "service"
            )
        }
    }

    @Test
    fun `test constraints on challenges - login - happy`() {
        with(initTest()) {
            val user = userDAO.withUser()
            TwoFactorChallenge(
                TwoFactorChallengeType.LOGIN.name,
                "id",
                Time.now(),
                TwoFactorCredentials(user, "secret", true, 42),
                "service"
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test constraints on challenges - setup - not happy`() {
        with(initTest()) {
            val user = userDAO.withUser()
            TwoFactorChallenge(
                TwoFactorChallengeType.SETUP.name,
                "id",
                Time.now(),
                TwoFactorCredentials(user, "secret", true, 42)
            )
        }
    }

    @Test
    fun `test constraints on challenges - setup - happy`() {
        with(initTest()) {
            val user = userDAO.withUser()
            TwoFactorChallenge(
                TwoFactorChallengeType.SETUP.name,
                "id",
                Time.now(),
                TwoFactorCredentials(user, "secret", false, 42)
            )
        }
    }
}
