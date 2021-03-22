package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import kotlin.test.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.project.api.LeaveProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.TransferPiRoleRequest
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.test.assertThatInstance

data class CreatedProvider(
    val providerContactClient: AuthenticatedClient,
    val providerContactUsername: String,
    val spec: ProviderSpecification,
    val rootProject: String,
    val project: String,
)

suspend fun createProvider(
    rootProject: String? = null,
    spec: ProviderSpecification = ProviderSpecification(
        id = "testprovider",
        domain = "provider.example.com",
        https = true,
    ),
): CreatedProvider {
    val rootProjectResolved = rootProject ?: initializeRootProject()
    // 1. UCloud administrator creates a project
    val normalProject = initializeNormalProject(rootProjectResolved, userRole = Role.ADMIN)

    // 2. UCloud administrator initializes a new provider
    val createdProvider = Providers.create.call(
        bulkRequestOf(spec),
        normalProject.piClient.withProject(normalProject.projectId)
    ).orThrow()

    // 3. Invite normal user (e.g. contact person at provider) to this project
    val (providerContactClient, providerContactUsername) = createUser(role = Role.USER)
    addMemberToProject(
        normalProject.projectId,
        normalProject.piClient,
        providerContactClient,
        providerContactUsername
    )

    Projects.transferPiRole.call(
        TransferPiRoleRequest(providerContactUsername),
        normalProject.piClient.withProject(normalProject.projectId)
    ).orThrow()

    Projects.leaveProject.call(
        LeaveProjectRequest,
        normalProject.piClient.withProject(normalProject.projectId)
    ).orThrow()

    // 4. Contact person can now read their key information
    assertThatInstance(
        Providers.browse.call(
            ProvidersBrowseRequest(),
            providerContactClient.withProject(normalProject.projectId)
        ).orThrow(),
        "has a single provider"
    ) { it.items.firstOrNull()?.id == createdProvider.responses.single().id }

    val providerInfo = Providers.retrieve.call(
        ProvidersRetrieveRequest(createdProvider.responses.single().id),
        providerContactClient
    ).orThrow()

    assertThatInstance(providerInfo.refreshToken, "refreshToken is not blank") { it.isNotBlank() }
    assertThatInstance(providerInfo.publicKey, "publicKey is not blank") { it.isNotBlank() }

    return CreatedProvider(
        providerContactClient,
        providerContactUsername,
        spec,
        rootProjectResolved,
        normalProject.projectId
    )
}

class ProviderTest : IntegrationTest() {
    @Test
    fun `test creation of provider`() = t {
        createProvider()
    }

    @Test
    fun `test creation of access token as provider`() = t {
        val provider = createProvider()
        val client = provider.providerContactClient
        val providerId = provider.spec.id

        val providerInfo = Providers.retrieve.call(
            ProvidersRetrieveRequest(providerId),
            client
        ).orThrow()

        val authenticator = RefreshingJWTAuthenticator(
            serviceClient.client,
            JwtRefresher.Provider(providerInfo.refreshToken)
        )

        assertThatInstance(authenticator.retrieveTokenRefreshIfNeeded(), "is not blank") { it.isNotBlank() }
    }

    @Test
    fun `test renewal of access token invalidates the old refresh token`() = t {
        val provider = createProvider()
        val client = provider.providerContactClient
        val providerId = provider.spec.id

        val providerInfo = Providers.retrieve.call(
            ProvidersRetrieveRequest(providerId),
            client
        ).orThrow()

        val oldAuthenticator = RefreshingJWTAuthenticator(
            serviceClient.client,
            JwtRefresher.Provider(providerInfo.refreshToken)
        )

        Providers.renewToken.call(
            bulkRequestOf(ProvidersRenewRefreshTokenRequestItem(providerId)),
            client
        ).orThrow()

        assertFails {
            oldAuthenticator.retrieveTokenRefreshIfNeeded()
        }

        val newProviderInfo = Providers.retrieve.call(
            ProvidersRetrieveRequest(providerId),
            client
        ).orThrow()

        val newAuthenticator = RefreshingJWTAuthenticator(
            serviceClient.client,
            JwtRefresher.Provider(newProviderInfo.refreshToken)
        )

        assertThatInstance(newAuthenticator.retrieveTokenRefreshIfNeeded(), "is not blank") { it.isNotBlank() }
    }

    @Test
    fun `test that ACLs are enforced`() = t {
        val provider = createProvider()
        val user = createUser()

        // 1. Check that the user cannot read data about our provider
        assertFails {
            Providers.retrieve.call(
                ProvidersRetrieveRequest(provider.spec.id),
                user.client
            ).orThrow()
        }

        // 2. Invite user to project
        addMemberToProject(provider.project, provider.providerContactClient, user.client, user.username)

        // 3. User should not be able to use it (yet)
        assertFails {
            Providers.retrieve.call(
                ProvidersRetrieveRequest(provider.spec.id),
                user.client
            ).orThrow()
        }

        // 4. Let's add them to the ACL
        val group = createGroup(
            NormalProjectInitialization(
                provider.providerContactClient,
                provider.providerContactUsername,
                provider.project
            ),
            setOf(user.username)
        )

        Providers.updateAcl.call(
            bulkRequestOf(
                ProvidersUpdateAclRequestItem(
                    provider.spec.id,
                    listOf(
                        ResourceAclEntry(
                            AclEntity.ProjectGroup(provider.project, group.groupId),
                            listOf(ProviderAclPermission.EDIT)
                        )
                    )
                )
            ),
            provider.providerContactClient
        ).orThrow()

        // 5. And check that they can read this information
        Providers.retrieve.call(
            ProvidersRetrieveRequest(provider.spec.id),
            user.client
        ).orThrow()
    }
}
