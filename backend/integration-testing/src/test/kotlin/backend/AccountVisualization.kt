package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.UsageRequest
import dk.sdu.cloud.accounting.api.Visualization
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.test.assertThatInstance
import org.junit.Ignore
import org.junit.Test
import kotlin.math.abs

class AccountVisualizationTest : IntegrationTest() {
    @Test
    fun `test usage (no data)`() = t {
        val user = createUser()
        val usage = Visualization.usage.call(
            UsageRequest(1000L, Time.now() - 60_000, Time.now()),
            user.client
        ).orThrow()

        assertThatInstance(
            usage.charts,
            "should contain a placeholder"
        ) { it.single().provider == "none" }
    }

    @Test
    fun `test usage (no data - with wallets)`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val now = Time.now()
        val usage = Visualization.usage.call(
            UsageRequest(AN_HOUR, now - AN_HOUR * 24, now),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            usage.charts,
            "should contain a placeholder"
        ) { it.single().provider == "none" }
    }

    @Test
    fun `test usage precision`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val wallet = Wallet(project.projectId, WalletOwnerType.PROJECT, sampleCompute.category)
        val charge = 10.DKK
        reserveCredits(wallet, charge, chargeImmediately = true)

        val now = Time.now()
        val period = 30_000
        val usage = Visualization.usage.call(
            UsageRequest(
                1000L,
                now - period,
                now
            ),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            usage,
            "contains the correct usage"
        ) { resp ->
            val chart = resp.charts.single()
            val line = chart.lines.find { it.category == wallet.paysFor.id } ?: error("Could not find wallet")
            line.points.fold(0L) { acc, usagePoint ->  acc + usagePoint.creditsUsed } == charge
        }

        assertThatInstance(
            usage.charts.single().lines.single().points.map { abs(now - it.timestamp) },
            "has timestamps within range"
        ) { it.all { it < period + 1000 } }
    }

    @Test
    fun `test usage permissions`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val wallet = Wallet(project.projectId, WalletOwnerType.PROJECT, sampleCompute.category)
        val user = createUser()
        addMemberToProject(project.projectId, project.piClient, user.client, user.username)
        val charge = 10.DKK
        reserveCredits(wallet, charge, chargeImmediately = true)

        val now = Time.now()
        val period = 30_000
        val usage = Visualization.usage.call(
            UsageRequest(
                1000L,
                now - period,
                now
            ),
            user.client.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            usage,
            "contains the correct usage"
        ) { resp ->
            val chart = resp.charts.single()
            val line = chart.lines.find { it.category == wallet.paysFor.id } ?: error("Could not find wallet")
            line.points.fold(0L) { acc, usagePoint ->  acc + usagePoint.creditsUsed } == charge
        }

        assertThatInstance(
            usage.charts.single().lines.single().points.map { abs(now - it.timestamp) },
            "has timestamps within range"
        ) { it.all { it < period + 1000 } }
    }

    @Test
    fun `test usage permissions (invalid)`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val wallet = Wallet(project.projectId, WalletOwnerType.PROJECT, sampleCompute.category)
        val user = createUser()
        val charge = 10.DKK
        reserveCredits(wallet, charge, chargeImmediately = true)

        val now = Time.now()
        val period = 30_000
        try {
            Visualization.usage.call(
                UsageRequest(
                    1000L,
                    now - period,
                    now
                ),
                user.client.withProject(project.projectId)
            ).orThrow()
            assert(false)
        } catch (ex: RPCException) {
            assertThatInstance(ex.httpStatusCode, "is a user error") { it.value in 400..499 }
        }
    }

    // TODO more advanced tests require us to mock the time source

    companion object {
        private const val AN_HOUR = 3600L * 1000
    }
}
