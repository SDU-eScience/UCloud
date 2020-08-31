package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.project.api.ChangeUserRoleRequest
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.test.assertThatInstance
import io.ktor.http.isSuccess
import org.junit.Ignore
import org.junit.Test

suspend fun createGift(
    projectId: String,
    client: AuthenticatedClient
): IngoingCallResponse<CreateGiftResponse, CommonErrorMessage> {
    return Gifts.createGift.call(
        CreateGiftRequest(
            0,
            projectId,
            "My Gift",
            "Free stuff",
            listOf(ResourceRequest.fromProduct(sampleCompute, 1000.DKK)),
            listOf(UserCriteria.Anyone())
        ),
        client
    )
}
@Ignore
class GiftTests : IntegrationTest() {
    @Test
    fun `test permissions for createGift`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val adminUser = createUser()
        val member = createUser()
        val outsideUser = createUser()

        addMemberToProject(project.projectId, project.piClient, adminUser.client, adminUser.username)
        Projects.changeUserRole.call(
            ChangeUserRoleRequest(project.projectId, adminUser.username, ProjectRole.ADMIN),
            project.piClient
        ).orThrow()

        addMemberToProject(project.projectId, project.piClient, member.client, member.username)

        createGift(project.projectId, project.piClient).orThrow().also { resp ->
            Gifts.deleteGift.call(DeleteGiftRequest(resp.id), project.piClient).orThrow()
        }
        createGift(project.projectId, adminUser.client).orThrow().also { resp ->
            assertThatInstance(Gifts.deleteGift.call(DeleteGiftRequest(resp.id), member.client), "fails") {
                !it.statusCode.isSuccess()
            }
            Gifts.deleteGift.call(DeleteGiftRequest(resp.id), adminUser.client).orThrow()
        }
        assertThatInstance(createGift(project.projectId, member.client), "fails") { !it.statusCode.isSuccess() }
        assertThatInstance(createGift(project.projectId, outsideUser.client), "fails") { !it.statusCode.isSuccess() }
    }

    @Test
    fun `we can list gifts`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val outsideUser = createUser()

        val id = createGift(project.projectId, project.piClient).orThrow().id
        assertThatInstance(
            Gifts.listGifts.call(ListGiftsRequest, project.piClient.withProject(project.projectId)).orThrow().gifts,
            "has the gift"
        ) { it.any { it.id == id } }

        assertThatInstance(
            Gifts.listGifts.call(ListGiftsRequest, outsideUser.client),
            "fails because we are not an admin"
        ) { !it.statusCode.isSuccess() }
    }

    @Test
    fun `we can view available gifts`() = t {
        val project = initializeNormalProject(initializeRootProject())

        val id = createGift(project.projectId, project.piClient).orThrow().id
        assertThatInstance(
            Gifts.availableGifts.call(ListGiftsRequest, project.piClient).orThrow().gifts,
            "has the gift"
        ) { it.any { it.id == id } }
    }

    @Test
    fun `we can claim a gift`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val outsideUser = createUser()

        val id = createGift(project.projectId, project.piClient).orThrow().id
        Gifts.claimGift.call(ClaimGiftRequest(id), outsideUser.client).orThrow()
        assertThatInstance(
            Gifts.claimGift.call(ClaimGiftRequest(id), outsideUser.client),
            "but not double claim them"
        ) { !it.statusCode.isSuccess() }

        val walletsAfter = Wallets.retrieveBalance.call(
            RetrieveBalanceRequest(outsideUser.username, WalletOwnerType.USER, false),
            outsideUser.client
        ).orThrow().wallets

        assertThatInstance(walletsAfter.find { it.wallet.paysFor == sampleCompute.category }, "is not empty") {
            it != null && it.balance > 0
        }
    }

    @Test
    fun `test claiming when we are out of resources`() = t {
        val project = initializeNormalProject(initializeRootProject(), initializeWallet = false)
        val users = (1..2).map { createUser() }
        Wallets.setBalance.call(
            SetBalanceRequest(
                Wallet(project.projectId, WalletOwnerType.PROJECT, sampleCompute.category),
                0L,
                1500.DKK
            ),
            serviceClient
        ).orThrow()

        val id = createGift(project.projectId, project.piClient).orThrow().id
        Gifts.claimGift.call(ClaimGiftRequest(id), users[0].client).orThrow()
        assertThatInstance(
            Gifts.claimGift.call(ClaimGiftRequest(id), users[1].client),
            "fails due to lack of funds"
        ) { !it.statusCode.isSuccess() }
    }
}
