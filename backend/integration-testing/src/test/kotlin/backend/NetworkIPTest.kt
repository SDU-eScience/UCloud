package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.app.kubernetes.api.K8Subnet
import dk.sdu.cloud.app.kubernetes.api.KubernetesIPMaintenanceBrowseRequest
import dk.sdu.cloud.app.kubernetes.api.KubernetesNetworkIPMaintenance
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import kotlin.test.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t

class NetworkIPTest : IntegrationTest() {
    // NOTE(Dan): This test suite does not attempt to test that the Kubernetes implementation actually work since this
    // would be quite complicated to implement inside of the testing.

    @Test
    fun `test lack of IP addresses`() = t {
        val rootProject = initializeRootProject()

        assertFails {
            NetworkIPs.create.call(
                bulkRequestOf(NetworkIPSpecification(sampleNetworkIp.toReference())),
                serviceClient.withProject(rootProject)
            ).orThrow()
        }
    }

    @Test
    fun `test creation of IP addresses`() = t {
        val rootProject = initializeRootProject()
        KubernetesNetworkIPMaintenance.create.call(
            bulkRequestOf(K8Subnet("10.0.0.0/24")),
            serviceClient
        ).orThrow()

        NetworkIPs.create.call(
            bulkRequestOf(NetworkIPSpecification(sampleNetworkIp.toReference())),
            serviceClient.withProject(rootProject)
        ).orThrow()
    }

    @Test
    fun `test creation of IP with firewall`() = t {
        val rootProject = initializeRootProject()
        KubernetesNetworkIPMaintenance.create.call(
            bulkRequestOf(K8Subnet("10.0.0.0/24")),
            serviceClient
        ).orThrow()

        NetworkIPs.create.call(
            bulkRequestOf(
                NetworkIPSpecification(
                    sampleNetworkIp.toReference(),
                    NetworkIPSpecification.Firewall(
                        listOf(PortRangeAndProto(0, 1024, IPProtocol.TCP))
                    )
                )
            ),
            serviceClient.withProject(rootProject)
        ).orThrow()
    }

    @Test
    fun `test creation of IP and update firewall`() = t {
        val rootProject = initializeRootProject()
        KubernetesNetworkIPMaintenance.create.call(
            bulkRequestOf(K8Subnet("10.0.0.0/24")),
            serviceClient
        ).orThrow()

        val firewall = NetworkIPSpecification.Firewall(
            listOf(PortRangeAndProto(0, 1024, IPProtocol.TCP))
        )

        val spec = NetworkIPSpecification(sampleNetworkIp.toReference(), firewall = NetworkIPSpecification.Firewall())

        val result = NetworkIPs.create.call(
            bulkRequestOf(spec),
            serviceClient.withProject(rootProject)
        ).orThrow()

        val retrievedBeforeUpdate = NetworkIPs.retrieve.call(
            NetworkIPsRetrieveRequest(result.ids.single()),
            serviceClient
        ).orThrow()

        assertEquals(spec, retrievedBeforeUpdate.specification)

        NetworkIPs.updateFirewall.call(
            bulkRequestOf(FirewallAndId(result.ids.single(), firewall)),
            serviceClient
        ).orThrow()

        val retrievedAfterUpdate = NetworkIPs.retrieve.call(
            NetworkIPsRetrieveRequest(result.ids.single()),
            serviceClient
        ).orThrow()

        assertEquals(spec.copy(firewall = firewall), retrievedAfterUpdate.specification)
    }

    @Test
    fun `test pool status`() = t {
        val rootProject = initializeRootProject()
        val cidr = "10.0.0.0/24"
        KubernetesNetworkIPMaintenance.create.call(
            bulkRequestOf(K8Subnet(cidr)),
            serviceClient
        ).orThrow()

        assertEquals(
            cidr,
            KubernetesNetworkIPMaintenance.browse.call(
                KubernetesIPMaintenanceBrowseRequest(),
                serviceClient
            ).orThrow().items.single().externalCidr
        )

        assertEquals(
            256,
            KubernetesNetworkIPMaintenance.retrieveStatus.call(
                Unit,
                serviceClient
            ).orThrow().capacity
        )

        assertEquals(
            0,
            KubernetesNetworkIPMaintenance.retrieveStatus.call(
                Unit,
                serviceClient
            ).orThrow().used
        )

        NetworkIPs.create.call(
            bulkRequestOf(NetworkIPSpecification(sampleNetworkIp.toReference())),
            serviceClient.withProject(rootProject)
        ).orThrow()

        assertEquals(
            256,
            KubernetesNetworkIPMaintenance.retrieveStatus.call(
                Unit,
                serviceClient
            ).orThrow().capacity
        )

        assertEquals(
            1,
            KubernetesNetworkIPMaintenance.retrieveStatus.call(
                Unit,
                serviceClient
            ).orThrow().used
        )
    }
}
