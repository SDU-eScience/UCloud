package dk.sdu.cloud.integration.backend.compute

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.backend.*
import dk.sdu.cloud.integration.utils.*
import dk.sdu.cloud.provider.api.Permission
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals

class IngressTest : IntegrationTest() {
    data class TestCase(
        val title: String,
        val products: List<Product.Ingress>,
        val deadlineForReady: Int = 60 * 5,
    )

    override fun defineTests() {
        val cases: List<TestCase> = runBlocking {
            val allProducts = findProducts(findProviderIds())
            val productsByProviders = allProducts.groupBy { it.category.provider }

            productsByProviders.mapNotNull { (provider, products) ->
                val ingresses = products.filterIsInstance<Product.Ingress>()

                if (ingresses.isEmpty()) null
                else TestCase(provider, ingresses)
            }
        }

        for (case in cases) {
            resourceUsageTest(
                "Ingress @ ${case.title}",
                Ingresses,
                case.products,
                flagFactory = { flags ->
                    IngressIncludeFlags(
                        includeOthers = flags.includeOthers,
                        includeUpdates = flags.includeUpdates,
                        includeSupport = flags.includeSupport,
                        includeProduct = flags.includeProduct,
                        filterCreatedBy = flags.filterCreatedBy,
                        filterCreatedAfter = flags.filterCreatedAfter,
                        filterCreatedBefore = flags.filterCreatedBefore,
                        filterProvider = flags.filterProvider,
                        filterProductId = flags.filterProductId,
                        filterProductCategory = flags.filterProductCategory,
                        filterProviderIds = flags.filterProviderIds,
                        filterIds = flags.filterIds,
                        hideProductId = flags.hideProductId,
                        hideProductCategory = flags.hideProductCategory,
                        hideProvider = flags.hideProvider,
                    )
                },
                additionalTesting = { input, resources ->
                    val filterId = resources.joinToString(",") { it.id }
                    retrySection(case.deadlineForReady, delay = 1000L) {
                        val items = Ingresses.browse.call(
                            ResourceBrowseRequest(IngressIncludeFlags(filterIds = filterId)),
                            adminClient.withProject(project)
                        ).orThrow().items

                        val expectedSize = resources.size - input.delete.count { it }
                        assertEquals(expectedSize, items.size)

                        val allReady = items.all { it.status.state == IngressState.READY }
                        if (!allReady) throw IllegalStateException("Ingresses are not ready yet: $items")
                    }
                },
                caseBuilder = {
                    for (product in case.products) {
                        for (count in 1..3) {
                            for (userType in UserType.values()) {
                                case("Simple test: $userType (count = $count)") {
                                    input(
                                        ResourceUsageTestInput(
                                            specs = {
                                                val support = findSupport<Product.Ingress, IngressSupport>(product,
                                                    project, client(userType)).support

                                                val prefix = support.domainPrefix
                                                val suffix = support.domainSuffix

                                                (0 until count).map { idx ->
                                                    IngressSpecification(
                                                        "${prefix}-${generateId("test")}${suffix}",
                                                        product.toReference()
                                                    )
                                                }
                                            },
                                            creator = userType
                                        )
                                    )

                                    check {
                                        // All good
                                    }
                                }
                            }
                        }

                        case("Permission updates") {
                            input(
                                ResourceUsageTestInput(
                                    specs = {
                                        val support = findSupport<Product.Ingress, IngressSupport>(product,
                                            project, client(UserType.PI)).support

                                        listOf(
                                            IngressSpecification(
                                                support.domainPrefix + generateId("test") + support.domainSuffix,
                                                product.toReference()
                                            )
                                        )
                                    },
                                    listOf("G1"),
                                    listOf(
                                        PartialAclUpdate(
                                            listOf(GroupAndPermission("G1", listOf(Permission.READ))),
                                            emptyList()
                                        )
                                    )
                                )
                            )

                            expectSuccess()
                        }


                        case("Deletion") {
                            input(
                                ResourceUsageTestInput(
                                    specs = {
                                        val support = findSupport<Product.Ingress, IngressSupport>(product,
                                            project, client(UserType.PI)).support

                                        listOf(
                                            IngressSpecification(
                                                support.domainPrefix + generateId("test") + support.domainSuffix,
                                                product.toReference()
                                            )
                                        )
                                    },
                                    delete = listOf(true)
                                )
                            )

                            expectSuccess()
                        }
                    }
                }
            )
        }
    }

    /*
        run {
            class Input(
                val createdBy: UserType,
                val accessedBy: UserType
            )

            class Output(
                val didCreate: Boolean,
                val resourceFromBrowse: Ingress?,
                val resourceFromRetrieve: Ingress?,
            )

            test<Input, Output>("Resource ACL checks (Using dummy ingress)") {
                execute {
                    dummyCase.initialization()
                    val ctx = initializeResourceTestContext(dummyCase.products, emptyList())
                    val clientToCreateWith = ctx.client(input.createdBy).withProject(ctx.project)
                    val clientToAccessWith = ctx.client(input.accessedBy).withProject(ctx.project)

                    val id = Ingresses.create.call(
                        bulkRequestOf(
                            IngressSpecification(
                                dummyCase.prefix + "my-domain" + dummyCase.suffix,
                                dummyCase.products.first().toReference()
                            )
                        ),
                        clientToCreateWith
                    ).orNull()?.responses?.singleOrNull()

                    val resourceFromRetrieve = if (id != null) {
                        Ingresses.retrieve.call(
                            ResourceRetrieveRequest(
                                IngressIncludeFlags(
                                    includeOthers = true,
                                    includeUpdates = true
                                ),
                                id.id
                            ),
                            clientToAccessWith
                        ).orNull()
                    } else {
                        null
                    }

                    val resourceFromBrowse = Ingresses.browse.call(
                        ResourceBrowseRequest(
                            IngressIncludeFlags(
                                includeOthers = true,
                                includeUpdates = true
                            )
                        ),
                        clientToAccessWith
                    ).orNull()?.items?.firstOrNull()

                    Output(id != null, resourceFromBrowse, resourceFromRetrieve)
                }

                fun success(output: Output) {
                    assertTrue { output.didCreate }
                    assertNotNull(output.resourceFromRetrieve)
                    assertEquals(output.resourceFromBrowse?.specification, output.resourceFromRetrieve.specification)
                }

                fun failureToRetrieve(output: Output) {
                    assertTrue { output.didCreate }
                    assertEquals(null, output.resourceFromRetrieve)
                    assertEquals(null, output.resourceFromBrowse)
                }

                fun failureToCreate(output: Output) {
                    assertTrue { !output.didCreate }
                    assertEquals(null, output.resourceFromRetrieve)
                    assertEquals(null, output.resourceFromBrowse)
                }

                run {
                    // PI + X
                    case("PI + PI") {
                        input(Input(UserType.PI, UserType.PI))
                        check { success(output) }
                    }

                    case("PI + Admin") {
                        input(Input(UserType.PI, UserType.ADMIN))
                        check { success(output) }
                    }

                    case("PI + Member") {
                        input(Input(UserType.PI, UserType.MEMBER))
                        check { failureToRetrieve(output) }
                    }

                    case("PI + User") {
                        input(Input(UserType.PI, UserType.SOME_OTHER_USER))
                        check { failureToRetrieve(output) }
                    }
                }

                run {
                    // Admin + X
                    case("Admin + PI") {
                        input(Input(UserType.ADMIN, UserType.PI))
                        check { success(output) }
                    }

                    case("Admin + Admin") {
                        input(Input(UserType.ADMIN, UserType.ADMIN))
                        check { success(output) }
                    }

                    case("Admin + Member") {
                        input(Input(UserType.ADMIN, UserType.MEMBER))
                        check { failureToRetrieve(output) }
                    }

                    case("Admin + User") {
                        input(Input(UserType.ADMIN, UserType.SOME_OTHER_USER))
                        check { failureToRetrieve(output) }
                    }
                }

                run {
                    // Member + X
                    case("Member + PI") {
                        input(Input(UserType.MEMBER, UserType.PI))
                        check { success(output) }
                    }

                    case("Member + Admin") {
                        input(Input(UserType.MEMBER, UserType.ADMIN))
                        check { success(output) }
                    }

                    case("Member + Member") {
                        input(Input(UserType.MEMBER, UserType.MEMBER))
                        check { success(output) }
                    }

                    case("Member + User") {
                        input(Input(UserType.MEMBER, UserType.SOME_OTHER_USER))
                        check { failureToRetrieve(output) }
                    }
                }

                run {
                    // User + X
                    case("User + PI") {
                        input(Input(UserType.SOME_OTHER_USER, UserType.PI))
                        check { failureToCreate(output) }
                    }

                    case("User + Admin") {
                        input(Input(UserType.SOME_OTHER_USER, UserType.ADMIN))
                        check { failureToCreate(output) }
                    }

                    case("User + Member") {
                        input(Input(UserType.SOME_OTHER_USER, UserType.MEMBER))
                        check { failureToCreate(output) }
                    }

                    case("User + User") {
                        input(Input(UserType.SOME_OTHER_USER, UserType.SOME_OTHER_USER))
                        check { failureToCreate(output) }
                    }
                }
            }
        }

        run {
            class ACLChange(val group: String, val permissions: List<Permission>)

            class Input(
                val updates: List<ACLChange>,
                val groupCreator: String,
                val groupAccessedBy: List<String>,
            ) {
                init {
                    require(groupAccessedBy.size == updates.size) { "groupAccessedBy must be equal in size to updates" }
                }
            }

            class Output(
                val didCreate: Boolean,
                val resourcesByRetrieve: List<Ingress?>,
            )

            test<Input, Output>("Group ACLs") {
                execute {
                    dummyCase.initialization()
                    val groups = (input.updates.map { it.group } + input.groupAccessedBy + input.groupCreator).toSet().toList()
                    val ctx = initializeResourceTestContext(dummyCase.products, groups)
                    val usersInGroups = groups.associateWith { createUser() }
                    for ((group, user) in usersInGroups) {
                        addMemberToProject(ctx.project, ctx.piClient, user.client, user.username)
                        ProjectGroups.addGroupMember.call(
                            AddGroupMemberRequest(ctx.groupNamesToId.getValue(group), user.username),
                            ctx.piClient.withProject(ctx.project)
                        ).orThrow()
                    }

                    val creatorClient = usersInGroups.getValue(input.groupCreator).client.withProject(ctx.project)
                    val id = Ingresses.create.call(
                        bulkRequestOf(
                            IngressSpecification(
                                dummyCase.prefix + "my-domain" + dummyCase.suffix,
                                dummyCase.products.first().toReference()
                            )
                        ),
                        creatorClient
                    ).orNull()?.responses?.singleOrNull()

                    val resourcesByRetrieve = if (id == null) {
                        input.updates.map { null }
                    } else {
                        input.updates.zip(input.groupAccessedBy).map { (update, groupToAccess) ->
                            val accessorClient = usersInGroups.getValue(groupToAccess).client.withProject(ctx.project)
                            if (update.permissions.isNotEmpty()) {
                                Ingresses.updateAcl.call(
                                    bulkRequestOf(
                                        UpdatedAcl(
                                            id.id,
                                            listOf(
                                                ResourceAclEntry(
                                                    AclEntity.ProjectGroup(ctx.project, ctx.groupNamesToId.getValue(update.group)),
                                                    update.permissions
                                                )
                                            ),
                                            emptyList()
                                        ),
                                    ),
                                    ctx.piClient
                                ).orThrow()
                            }

                            Ingresses.retrieve.call(
                                ResourceRetrieveRequest(
                                    IngressIncludeFlags(includeOthers = true),
                                    id.id
                                ),
                                accessorClient
                            ).orNull()
                        }
                    }

                    Output(id != null, resourcesByRetrieve)
                }

                case("Simple read test") {
                    input(Input(
                        listOf(ACLChange("G2", listOf(Permission.READ))),
                        "G1",
                        listOf("G2")
                    ))

                    check {
                        assertTrue(output.didCreate)
                        assertEquals(1, output.resourcesByRetrieve.size)
                        assertNotNull(output.resourcesByRetrieve.single())
                    }
                }

                case("Simple test with no permissions") {
                    input(Input(
                        listOf(ACLChange("G2", emptyList())),
                        "G1",
                        listOf("G2")
                    ))

                    check {
                        assertTrue(output.didCreate)
                        assertEquals(1, output.resourcesByRetrieve.size)
                        assertEquals(null, output.resourcesByRetrieve.single())
                    }
                }
            }
        }
     */
}
