package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.utils.createUser
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.provider.api.*

enum class UserType(val usuallyHasPermissions: Boolean) {
    PI(true),
    ADMIN(true),
    MEMBER(true),
    SOME_OTHER_USER(false),
}

data class GroupAndPermission(val groupName: String, val permissions: List<Permission>)
data class PartialAclUpdate(val added: List<GroupAndPermission>, val removed: List<String>)

class ResourceUsageTestInput<Prod : Product, Supp : ProductSupport, Resc : Resource<Prod, Supp>, Spec : ResourceSpecification>(
    val specs: List<Spec>,
    val groups: List<String> = emptyList(),
    val aclUpdates: List<PartialAclUpdate> = emptyList(),
    val delete: List<Boolean> = emptyList(),
    val creator: UserType = UserType.PI
)

class ResourceUsageTestOutput<Prod : Product, Supp : ProductSupport, Resc : Resource<Prod, Supp>, Spec : ResourceSpecification>(
    val page: PageV2<Resc>?
)

open class ResourceUsageTestContext(
    val project: String,
    val projectTitle: String,

    val piClient: AuthenticatedClient,
    val piUsername: String,
    val piPassword: String,

    val adminClient: AuthenticatedClient,
    val adminUsername: String,
    val adminPassword: String,

    val memberClient: AuthenticatedClient,
    val memberUsername: String,
    val memberPassword: String,

    val otherUserClient: AuthenticatedClient,
    val otherUserUsername: String,
    val otherUserPassword: String,

    val groupNamesToId: Map<String, String>
) {
    fun client(type: UserType): AuthenticatedClient {
        return when (type) {
            UserType.PI -> piClient
            UserType.ADMIN -> adminClient
            UserType.MEMBER -> memberClient
            UserType.SOME_OTHER_USER -> otherUserClient
        }
    }

    fun username(type: UserType): String {
        return when (type) {
            UserType.PI -> piUsername
            UserType.ADMIN -> adminUsername
            UserType.MEMBER -> memberUsername
            UserType.SOME_OTHER_USER -> otherUserUsername
        }
    }
}

suspend fun initializeResourceTestContext(
    products: List<Product>,
    groups: List<String>,
): ResourceUsageTestContext {
    val (piClient, piUsername, piPassword) = createUser()
    val (adminClient, adminUsername, adminPassword) = createUser()
    val (memberClient, memberUsername, memberPassword) = createUser()
    val (otherUserClient, otherUserUsername, otherUserPassword) = createUser()

    val (project, title) = initializeRootProjectWithTitle(piUsername, initializeWallet = false)
    initializeWallets(
        WalletOwner.Project(project),
        products = products
    )
    addMemberToProject(project, piClient, adminClient, adminUsername)
    addMemberToProject(project, piClient, memberClient, memberUsername)
    Projects.changeUserRole.call(
        ChangeUserRoleRequest(project, adminUsername, ProjectRole.ADMIN),
        piClient
    ).orThrow()

    val groupNamesToId = groups.associateWith { groupName ->
        ProjectGroups.create.call(
            CreateGroupRequest(groupName),
            piClient.withProject(project)
        ).orThrow().id
    }

    return ResourceUsageTestContext(
        project, title,
        piClient, piUsername, piPassword,
        adminClient, adminUsername, adminPassword,
        memberClient, memberUsername, memberPassword,
        otherUserClient, otherUserUsername, otherUserPassword,
        groupNamesToId
    )
}

fun <Prod : Product, Supp : ProductSupport, Resc : Resource<Prod, Supp>, Spec : ResourceSpecification, Flags : ResourceIncludeFlags> IntegrationTest.resourceUsageTest(
    title: String,
    api: ResourceApi<Resc, Spec, *, Flags, *, Prod, Supp>,
    products: List<Prod>,
    flagFactory: (input: SimpleResourceIncludeFlags) -> Flags,
    createResource: (suspend ResourceUsageTestContext.(input: ResourceUsageTestInput<Prod, Supp, Resc, Spec>) -> Unit)? = null,
    initialization: suspend () -> Unit = {},
    additionalTesting: suspend ResourceUsageTestContext.(input: ResourceUsageTestInput<Prod, Supp, Resc, Spec>, resources: List<Resc>) -> Unit = { input, resc -> },
    caseBuilder: UCloudTestSuiteBuilder<ResourceUsageTestInput<Prod, Supp, Resc, Spec>, ResourceUsageTestOutput<Prod, Supp, Resc, Spec>>.() -> Unit,
) {
    test<ResourceUsageTestInput<Prod, Supp, Resc, Spec>, ResourceUsageTestOutput<Prod, Supp, Resc, Spec>>(
        "$title: Usage Test",
        builder = {
            execute {
                initialization()

                val context = initializeResourceTestContext(products, input.groups)
                with(context) {
                    val clientForRpc = when (input.creator) {
                        UserType.PI -> piClient
                        UserType.ADMIN -> adminClient
                        UserType.MEMBER -> memberClient
                        UserType.SOME_OTHER_USER -> otherUserClient
                    }

                    if (createResource == null) {
                        val response = api.create!!.call(
                            BulkRequest(input.specs),
                            clientForRpc.withProject(project)
                        )

                        if (input.creator.usuallyHasPermissions) {
                            response.orThrow()
                        } else {
                            assertThatInstance(response.statusCode, "fails with a permission like error") {
                                it == HttpStatusCode.NotFound || it == HttpStatusCode.BadRequest ||
                                    it == HttpStatusCode.Forbidden
                            }
                        }
                    } else {
                        createResource(input)
                    }

                    val page = api.browse.call(
                        ResourceBrowseRequest(
                            flagFactory(SimpleResourceIncludeFlags()),
                            itemsPerPage = 250
                        ),
                        clientForRpc.withProject(project)
                    ).orNull()

                    if (input.creator.usuallyHasPermissions) {
                        assertThatInstance(page, "should contain the resources") {
                            if (page == null) return@assertThatInstance false

                            input.specs.all { createdSpec ->
                                page.items.any { returned -> returned.specification == createdSpec }
                            }
                        }
                    } else {
                        assertThatInstance(page, "should not succeed") { it?.items?.isEmpty() == true }
                    }

                    if (page != null && page.items.isNotEmpty()) {
                        val resources = input.specs.map { spec ->
                            page.items.find { it.specification == spec }!!
                        }

                        for ((index, update) in input.aclUpdates.withIndex()) {
                            val resource = resources[index]
                            val added = update.added.map {
                                ResourceAclEntry(
                                    AclEntity.ProjectGroup(project, groupNamesToId.getValue(it.groupName)),
                                    it.permissions
                                )
                            }

                            val deleted = update.removed.map {
                                AclEntity.ProjectGroup(project, groupNamesToId.getValue(it))
                            }

                            val updateRequest = bulkRequestOf(UpdatedAcl(resource.id, added, deleted))

                            api.updateAcl.call(updateRequest, piClient).orThrow()

                            assertThatInstance(
                                api.updateAcl.call(updateRequest, memberClient),
                                "fails with a permission denied"
                            ) { it.statusCode.value in 400..499 }

                            assertThatInstance(
                                api.updateAcl.call(updateRequest, otherUserClient),
                                "fails with a permission denied"
                            ) { it.statusCode.value in 400..499 }

                            val retrievedResource = api.retrieve.call(
                                ResourceRetrieveRequest(
                                    flagFactory(SimpleResourceIncludeFlags(includeOthers = true)),
                                    resource.id
                                ),
                                adminClient
                            ).orThrow()

                            assertThatInstance(retrievedResource.specification, "should match the original spec") {
                                it == resource.specification
                            }

                            assertThatInstance(
                                retrievedResource.permissions?.others,
                                "should match our update"
                            ) { entries ->
                                entries != null && added.all { addedEntry ->
                                    entries.any { actualEntry -> actualEntry == addedEntry }
                                }
                            }
                        }

                        for ((index, doDelete) in input.delete.withIndex()) {
                            if (!doDelete) continue
                            val resourceId = resources[index].id
                            val deleteRequest = bulkRequestOf(FindByStringId(resourceId))

                            assertThatInstance(
                                api.delete!!.call(deleteRequest, memberClient),
                                "fails with a permission denied"
                            ) { it.statusCode.value in 400..499 }

                            assertThatInstance(
                                api.delete!!.call(deleteRequest, otherUserClient),
                                "fails with a permission denied"
                            ) { it.statusCode.value in 400..499 }

                            api.delete!!.call(deleteRequest, adminClient).orThrow()

                            assertThatInstance(
                                api.retrieve.call(
                                    ResourceRetrieveRequest(
                                        flagFactory(SimpleResourceIncludeFlags(includeOthers = true)),
                                        resourceId
                                    ),
                                    adminClient
                                ),
                                "should have been deleted"
                            ) { it.statusCode == HttpStatusCode.NotFound }
                        }

                        additionalTesting(input, resources)
                    }

                    ResourceUsageTestOutput(page)
                }
            }

            caseBuilder()
        }
    )
}
