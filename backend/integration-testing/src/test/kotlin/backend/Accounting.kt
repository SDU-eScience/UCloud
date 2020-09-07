package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.webdav.services.UserClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.junit.Ignore
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

suspend fun addFundsToPersonalProject(
    rootProject: String,
    username: String,
    product: ProductCategoryId = sampleCompute.category,
    amount: Long = 10_000.DKK
) {
    Wallets.transferToPersonal.call(
        TransferToPersonalRequest(
            listOf(
                SingleTransferRequest(
                    "_UCloud",
                    amount,
                    Wallet(rootProject, WalletOwnerType.PROJECT, product),
                    Wallet(username, WalletOwnerType.USER, product)
                )
            )
        ),
        serviceClient
    ).orThrow()
}

suspend fun findPersonalWallet(
    username: String,
    client: AuthenticatedClient,
    product: ProductCategoryId
): WalletBalance? {
    return Wallets.retrieveBalance
        .call(
            RetrieveBalanceRequest(username, WalletOwnerType.USER),
            client
        ).orThrow()
        .wallets.find { it.wallet.paysFor == product }
}

suspend fun findProjectWallet(
    projectId: String,
    client: AuthenticatedClient,
    product: ProductCategoryId
): WalletBalance? {
    return Wallets.retrieveBalance
        .call(
            RetrieveBalanceRequest(projectId, WalletOwnerType.PROJECT),
            client
        ).orThrow()
        .wallets.find { it.wallet.paysFor == product }
}

suspend fun reserveCredits(
    wallet: Wallet,
    amount: Long,
    product: Product = sampleCompute,
    chargeImmediately: Boolean = false
): String {
    val id = UUID.randomUUID().toString()
    Wallets.reserveCredits.call(
        ReserveCreditsRequest(
            id,
            amount,
            Time.now() + 1000 * 60,
            wallet,
            "_UCloud",
            product.id,
            amount / product.pricePerUnit,
            chargeImmediately = chargeImmediately
        ),
        serviceClient
    ).orThrow()
    return id
}
@Ignore
class AccountingTest : IntegrationTest() {
    @Test
    fun `test simple case accounting`() = t {
        createSampleProducts()
        val root = initializeRootProject(initializeWallet = false)
        val wallet = Wallet(root, WalletOwnerType.PROJECT, sampleCompute.category)
        val initialBalance = 1000.DKK
        val reservation = 10.DKK

        Wallets.setBalance.call(
            SetBalanceRequest(
                wallet,
                0L,
                initialBalance
            ),
            serviceClient
        ).orThrow()

        assertThatInstance(
            Wallets.retrieveBalance.call(
                RetrieveBalanceRequest(wallet.id, wallet.type, false),
                serviceClient
            ).orThrow(),
            "has the new balance"
        ) { it.wallets.find { it.wallet == wallet }!!.balance == initialBalance }

        val reservationId = reserveCredits(wallet, reservation, sampleCompute)

        assertThatInstance(
            Wallets.retrieveBalance.call(
                RetrieveBalanceRequest(wallet.id, wallet.type, false),
                serviceClient
            ).orThrow(),
            "has the initial balance"
        ) { it.wallets.find { it.wallet == wallet }!!.balance == initialBalance }

        Wallets.chargeReservation.call(
            ChargeReservationRequest(reservationId, reservation, reservation / sampleCompute.pricePerUnit),
            serviceClient
        ).orThrow()

        assertThatInstance(
            Wallets.retrieveBalance.call(
                RetrieveBalanceRequest(wallet.id, wallet.type, false),
                serviceClient
            ).orThrow(),
            "has the updated balance"
        ) { it.wallets.find { it.wallet == wallet }!!.balance == initialBalance - reservation }
    }

    @Test
    fun `transfer to personal project`() = t {
        val initialBalance = 1000.DKK
        val transferAmount = 600.DKK
        val product = sampleCompute.category

        val root = initializeRootProject(amount = initialBalance)
        val user = createUser()
        assertThatInstance(
            findPersonalWallet(user.username, user.client, product),
            "is empty"
        ) { it == null || it.balance == 0L }

        val transferRequest = TransferToPersonalRequest(
            listOf(
                SingleTransferRequest(
                    "_UCloud",
                    transferAmount,
                    Wallet(root, WalletOwnerType.PROJECT, product),
                    Wallet(user.username, WalletOwnerType.USER, product)
                )
            )
        )

        Wallets.transferToPersonal.call(transferRequest, serviceClient).orThrow()

        assertThatInstance(
            findPersonalWallet(user.username, user.client, product),
            "has received the funds"
        ) { it != null && it.balance == transferAmount }

        assertThatInstance(
            findProjectWallet(root, serviceClient, product),
            "has had funds subtracted"
        ) { it != null && it.balance == initialBalance - transferAmount }

        assertThatInstance(
            Wallets.transferToPersonal.call(transferRequest, serviceClient),
            "fails due to lack of funds"
        ) { it.statusCode == HttpStatusCode.PaymentRequired }
    }

    @Test
    fun `test charging a reservation of too large size`() = t {
        val initialBalance = 1000.DKK
        val root = initializeRootProject(amount = initialBalance)
        try {
            reserveCredits(Wallet(root, WalletOwnerType.PROJECT, sampleCompute.category), initialBalance + 1)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(ex.httpStatusCode, HttpStatusCode.PaymentRequired)
        }
    }

    @Test
    fun `test reserving with negative amount`() = t {
        val initialBalance = 1000.DKK
        val root = initializeRootProject(amount = initialBalance)
        try {
            reserveCredits(Wallet(root, WalletOwnerType.PROJECT, sampleCompute.category), -1L)
            assert(false)
        } catch (ex: RPCException) {
            assertThatInstance(ex.httpStatusCode, "is a client error") { it.value in 400..499 }
        }
    }

    @Test
    fun `test charging negative amount`() = t {
        val initialBalance = 1000.DKK
        val root = initializeRootProject(amount = initialBalance)
        val id = reserveCredits(Wallet(root, WalletOwnerType.PROJECT, sampleCompute.category), 50.DKK)
        try {
            Wallets.chargeReservation.call(
                ChargeReservationRequest(id, -1L, 0L),
                serviceClient
            ).orThrow()
        } catch (ex: RPCException) {
            assertThatInstance(ex.httpStatusCode, "fails") { it.value in 400..499 }
        }
    }

    @Test
    fun `test that parents are all charged`() = t {
        val initialBalance = 1000.DKK
        val charge = 500.DKK
        val r = initializeNormalProject(initializeRootProject(), amount = initialBalance)
        val parents = arrayListOf(r.projectId)
        val category = sampleCompute.category

        repeat(5) {
            val element = Projects.create.call(
                CreateProjectRequest("T$it", parents.last()),
                r.piClient
            ).orThrow().id
            parents.add(element)

            Wallets.setBalance.call(
                SetBalanceRequest(Wallet(element, WalletOwnerType.PROJECT, category), 0L, initialBalance),
                r.piClient
            ).orThrow()
        }

        val id = reserveCredits(Wallet(parents.last(), WalletOwnerType.PROJECT, category), charge)
        Wallets.chargeReservation.call(
            ChargeReservationRequest(id, charge, 1),
            serviceClient
        ).orThrow()

        for (p in parents) {
            assertThatInstance(
                findProjectWallet(p, r.piClient, category),
                "has been charged"
            ) { it != null && it.balance == initialBalance - charge }
        }
    }

    @Test
    fun `test charging a personal project`() = t {
        val initialBalance = 1000.DKK
        val transferAmount = 600.DKK
        val chargeAmount = 200.DKK
        val product = sampleCompute.category

        val root = initializeRootProject(amount = initialBalance)
        val user = createUser()
        assertThatInstance(
            findPersonalWallet(user.username, user.client, product),
            "is empty"
        ) { it == null || it.balance == 0L }

        val transferRequest = TransferToPersonalRequest(
            listOf(
                SingleTransferRequest(
                    "_UCloud",
                    transferAmount,
                    Wallet(root, WalletOwnerType.PROJECT, product),
                    Wallet(user.username, WalletOwnerType.USER, product)
                )
            )
        )

        Wallets.transferToPersonal.call(transferRequest, serviceClient).orThrow()

        val id = reserveCredits(Wallet(user.username, WalletOwnerType.USER, product), chargeAmount)
        Wallets.chargeReservation.call(ChargeReservationRequest(id, chargeAmount, 1), serviceClient).orThrow()

        assertThatInstance(
            findPersonalWallet(user.username, user.client, product),
            "has been charged"
        ) { it != null && it.balance == transferAmount - chargeAmount }
    }

    @Test
    fun `test funds are checked locally`() = t {
        val initialBalance = 1000.DKK
        val charge = 500.DKK
        val r = initializeNormalProject(initializeRootProject(), amount = initialBalance)
        val parents = arrayListOf(r.projectId)
        val category = sampleCompute.category

        repeat(5) {
            val element = Projects.create.call(
                CreateProjectRequest("T$it", parents.last()),
                r.piClient
            ).orThrow().id
            parents.add(element)

            Wallets.setBalance.call(
                SetBalanceRequest(Wallet(element, WalletOwnerType.PROJECT, category), 0L, initialBalance),
                r.piClient
            ).orThrow()
        }

        Wallets.setBalance.call(
            SetBalanceRequest(Wallet(parents.last(), WalletOwnerType.PROJECT, category), initialBalance, 0L),
            r.piClient
        ).orThrow()

        try {
            reserveCredits(Wallet(parents.last(), WalletOwnerType.PROJECT, category), charge)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.PaymentRequired, ex.httpStatusCode)
        }
    }

    @Test
    fun `test funds are checked in ancestors`() = t {
        val initialBalance = 1000.DKK
        val r = initializeNormalProject(initializeRootProject(), amount = initialBalance)
        val parents = arrayListOf(r.projectId)
        val category = sampleCompute.category

        repeat(5) {
            val element = Projects.create.call(
                CreateProjectRequest("T$it", parents.last()),
                r.piClient
            ).orThrow().id
            parents.add(element)

            Wallets.setBalance.call(
                SetBalanceRequest(Wallet(element, WalletOwnerType.PROJECT, category), 0L, initialBalance),
                r.piClient
            ).orThrow()
        }

        Wallets.setBalance.call(
            SetBalanceRequest(Wallet(parents.last(), WalletOwnerType.PROJECT, category), initialBalance,
                initialBalance * 10),
            r.piClient
        ).orThrow()

        try {
            reserveCredits(Wallet(parents.last(), WalletOwnerType.PROJECT, category), initialBalance * 2)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.PaymentRequired, ex.httpStatusCode)
        }
    }
}
