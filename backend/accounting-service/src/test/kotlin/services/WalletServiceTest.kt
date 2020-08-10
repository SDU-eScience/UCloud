package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.api.AccountingServiceDescription
import dk.sdu.cloud.accounting.api.ReserveCreditsRequest
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.accounting.utils.insertAll
import dk.sdu.cloud.accounting.utils.project
import dk.sdu.cloud.accounting.utils.projectId
import dk.sdu.cloud.accounting.utils.walletProjectStandard
import dk.sdu.cloud.accounting.utils.walletUserGpu
import dk.sdu.cloud.accounting.utils.walletUserStandard
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.project.api.ExistsResponse
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.UserGroupSummary
import dk.sdu.cloud.project.api.UserStatusInProject
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.project.api.ViewAncestorsResponse
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import io.ktor.http.HttpStatusCode
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.joda.time.LocalDateTime
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WalletServiceTest {

    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDb: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db,embDb) = TestDB.from(AccountingServiceDescription)
            this.db = db
            this.embDb = embDb
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking {
                db.close()
            }
            embDb.close()
        }

    }

    fun truncateAccountingDB() {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        TRUNCATE 
                            wallets,
                            product_categories,
                            products,
                            accounting.job_completed_events,
                            transactions
                    """
                )
            }
        }
    }

    @BeforeTest
    fun before() {
        truncateAccountingDB()
        runBlocking {
            insertAll(db)
        }
    }

    @AfterTest
    fun after() {
        truncateAccountingDB()
    }

    val user = TestUsers.user
    val onBehalfofUser = Actor.SystemOnBehalfOfUser(user.username)

    @Test
    fun `Test get wallets`() {

        val client = ClientMock.authenticatedClient
        val pCache = ProjectCache(client)
        val verificationService = VerificationService(client)
        val walletService = BalanceService(pCache, verificationService, client)

        ClientMock.mockCallSuccess(
            UserDescriptions.lookupUsers,
            LookupUsersResponse(mapOf(
                user.username to UserLookup(user.username, user.uid, user.role))
            )
        )

        runBlocking {
            val wallets = walletService.getWalletsForAccount(
                db,
                onBehalfofUser,
                user.username,
                WalletOwnerType.USER,
                false
            )
            val gpuWallet = wallets.find { it.wallet.paysFor.id == "gpu" }
            val standardWallet = wallets.find { it.wallet.paysFor.id == "standard" }
            val cephfs = wallets.find { it.wallet.paysFor.id == "cephfs" }

            assertNotNull(gpuWallet)
            assertNotNull(standardWallet)
            assertNotNull(cephfs)
            assertEquals(
                gpuWallet.balance,
                300000
            )
            assertEquals(
                standardWallet.balance,
                200000
            )
            assertEquals(
                cephfs.balance,
                5000000
            )
        }

        ClientMock.mockCallSuccess(
            ProjectMembers.userStatus,
            UserStatusResponse(
                listOf(
                    UserStatusInProject(
                        projectId,
                        project.title,
                        ProjectMember(user.username, ProjectRole.ADMIN),
                        null
                    )
                ),
                listOf(
                    UserGroupSummary(
                        projectId,
                        "group",
                        user.username
                    )
                )
            )
        )

        ClientMock.mockCallSuccess(
            Projects.exists,
            ExistsResponse(true)
        )

        runBlocking {
            val wallets = walletService.getWalletsForAccount(
                db,
                onBehalfofUser,
                projectId,
                WalletOwnerType.PROJECT,
                false
            )
            val gpuWallet = wallets.find { it.wallet.paysFor.id == "gpu" }
            val standardWallet = wallets.find { it.wallet.paysFor.id == "standard" }
            val cephfs = wallets.find { it.wallet.paysFor.id == "cephfs" }

            assertNotNull(gpuWallet)
            assertNotNull(standardWallet)
            assertNotNull(cephfs)
            println(wallets)
            assertEquals(
                gpuWallet.balance,
                5000000
            )
            assertEquals(
                standardWallet.balance,
                1000000
            )
            assertEquals(
                cephfs.balance,
                10000000
            )
        }
    }

    @Test
    fun `Test Get and set Balance`() {
        val client = ClientMock.authenticatedClient
        val pCache = ProjectCache(client)
        val verificationService = VerificationService(client)
        val walletService = BalanceService(pCache, verificationService, client)

        runBlocking {
            val balance = walletService.getBalance(db, Actor.SystemOnBehalfOfUser(user.username), walletUserGpu, false)
            assertEquals(300000, balance.first)
            walletService.setBalance(db, Actor.System, walletUserGpu, 300000, 500000)

            val balanceAfter = walletService.getBalance(db, Actor.SystemOnBehalfOfUser(user.username), walletUserGpu, false)
            assertEquals(500000, balanceAfter.first)

            walletService.addToBalance(db, Actor.System, walletUserGpu, 500)

            val balanceAfterAdd = walletService.getBalance(db, Actor.SystemOnBehalfOfUser(user.username), walletUserGpu, false)
            assertEquals(500500, balanceAfterAdd.first)
        }
    }

    @Test
    fun `Test set Balance not allowed`() {
        val client = ClientMock.authenticatedClient
        val pCache = ProjectCache(client)
        val verificationService = VerificationService(client)
        val walletService = BalanceService(pCache, verificationService, client)

        runBlocking {
            try {
                walletService.setBalance(db, Actor.SystemOnBehalfOfUser(user.username), walletUserGpu, 300000, 500000)
            } catch (ex: RPCException) {
                if (ex.httpStatusCode != HttpStatusCode.Forbidden) {
                    assert(false)
                }
            }
        }
    }


    @Test
    fun `Test get and set reservation`() {
        val client = ClientMock.authenticatedClient
        val pCache = ProjectCache(client)
        val verificationService = VerificationService(client)
        val walletService = BalanceService(pCache, verificationService, client)

        ClientMock.mockCallSuccess(
            Projects.viewAncestors,
            emptyList()
        )

        runBlocking {
            walletService.reserveCredits(
                db,
                Actor.SystemOnBehalfOfUser(user.username),
                ReserveCreditsRequest(
                    "jobId",
                    25000,
                    LocalDateTime.now().plusHours(4).toDateTime().millis,
                    walletProjectStandard,
                    user.username,
                    walletUserStandard.paysFor.id,
                    400
                )
            )
            val reserved = walletService.getReservedCredits(db, walletProjectStandard)
            assertEquals(25000, reserved)
            walletService.chargeFromReservation(db, "jobId", 25000, 400)

            val reservedAfterCharge = walletService.getReservedCredits(db, walletProjectStandard)
            assertEquals(0, reservedAfterCharge)
        }
    }

    @Test
    fun `Test permission`() {
        val client = ClientMock.authenticatedClient
        val pCache = ProjectCache(client)
        val verificationService = VerificationService(client)
        val walletService = BalanceService(pCache, verificationService, client)

        runBlocking {
            walletService.requirePermissionToReadBalance(
                db,
                Actor.User(user),
                walletUserStandard.id,
                walletUserStandard.type
            )

            walletService.requirePermissionToWriteBalance(
                db,
                Actor.User(TestUsers.service),
                walletUserStandard.id,
                walletUserStandard.type
            )

            walletService.requirePermissionToTransferFromAccount(
                db,
                Actor.User(user),
                walletProjectStandard.id,
                walletProjectStandard.type
            )
        }
    }
}
