package dk.sdu.cloud.integration.backend.compute

import dk.sdu.cloud.Page
import dk.sdu.cloud.Role
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.utils.createApp
import dk.sdu.cloud.integration.utils.createTool
import dk.sdu.cloud.integration.utils.createUser
import kotlin.random.Random
import kotlin.test.assertEquals

class AppStoreTest : IntegrationTest() {
    override fun defineTests() {
        run {
            data class AppCreateInfo(
                val app: NameAndVersion,
                val public: Boolean,
                val tool: NameAndVersion,
            )

            class In(
                val userIsAdmin: Boolean,
                val toolsToCreate: List<NameAndVersion>,
                val appsToCreate: List<AppCreateInfo>,
                val searchQuery: String
            )

            class Out(
                val appsFound: Page<ApplicationSummaryWithFavorite>
            )

            test<In, Out>("simple search tests") {
                execute {
                    input.toolsToCreate.forEach {
                        createTool(it.name, it.version)
                    }
                    input.appsToCreate.forEach {
                        createApp(
                            name = it.app.name,
                            version = it.app.version,
                            toolName = it.tool.name,
                            toolVersion = it.tool.version,
                            isPublic = it.public
                        )

                        AppStore.findByNameAndVersion.call(
                            FindApplicationAndOptionalDependencies(
                                it.app.name,
                                it.app.version
                            ),
                            adminClient
                        ).orThrow()
                    }

                    val user =
                        if (input.userIsAdmin) {
                            createUser(role = Role.ADMIN)
                        } else {
                            createUser()
                        }
                    val appsFound = AppStore.searchApps.call(
                        AppSearchRequest(
                            input.searchQuery
                        ),
                        user.client
                    ).orThrow()

                    Out(
                        appsFound
                    )
                }

                case("single version - public - user") {
                    val nameToUsePrefix = "test-app-${Random.nextLong()}"
                    val nameToUse = "$nameToUsePrefix-foobar"

                    input(
                        In(
                            userIsAdmin = false,
                            toolsToCreate = listOf(NameAndVersion(nameToUse, "1.64.2")),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion(nameToUse, "1.64.2")
                                )
                            ),
                            searchQuery = nameToUsePrefix
                        )
                    )

                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals(nameToUse, output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.2", output.appsFound.items.first().metadata.version)
                    }
                }

                case("single version - public - admin") {
                    val nameToUsePrefix = "test-app-${Random.nextLong()}"
                    val nameToUse = "$nameToUsePrefix-foobar"

                    input(
                        In(
                            userIsAdmin = true,
                            toolsToCreate = listOf(NameAndVersion(nameToUse, "1.64.2")),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion(nameToUse, "1.64.2")
                                )
                            ),
                            searchQuery = nameToUsePrefix
                        )
                    )

                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals(nameToUse, output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.2", output.appsFound.items.first().metadata.version)
                    }
                }

                case("single version - non public - user") {
                    val nameToUsePrefix = "test-app-${Random.nextLong()}"
                    val nameToUse = "$nameToUsePrefix-foobar"

                    input(
                        In(
                            userIsAdmin = false,
                            toolsToCreate = listOf(NameAndVersion(nameToUse, "1.64.2")),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.2"),
                                    public = false,
                                    tool = NameAndVersion(nameToUse, "1.64.2")
                                )
                            ),
                            searchQuery = nameToUsePrefix
                        )
                    )

                    check {
                        assertEquals(0, output.appsFound.itemsInTotal)
                    }
                }

                case("single version - non public - admin") {
                    val nameToUsePrefix = "test-app-${Random.nextLong()}"
                    val nameToUse = "$nameToUsePrefix-foobar"

                    input(
                        In(
                            userIsAdmin = true,
                            toolsToCreate = listOf(
                                NameAndVersion(
                                    nameToUse,
                                    "1.64.2"
                                )
                            ),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.2"),
                                    public = false,
                                    tool = NameAndVersion(nameToUse, "1.64.2")
                                )
                            ),
                            searchQuery = nameToUsePrefix
                        )
                    )

                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals(nameToUse, output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.2", output.appsFound.items.first().metadata.version)
                    }
                }

                case("multiple versions - public - user") {
                    val nameToUsePrefix = "test-app-${Random.nextLong()}"
                    val nameToUse = "$nameToUsePrefix-foobar"

                    input(
                        In(
                            userIsAdmin = false,
                            toolsToCreate = listOf(
                                NameAndVersion(
                                    nameToUse,
                                    "1.64.2"
                                ),
                                NameAndVersion(
                                    nameToUse,
                                    "1.64.40"
                                )
                            ),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion(nameToUse, "1.64.2")
                                ),
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.40"),
                                    public = true,
                                    tool = NameAndVersion(nameToUse, "1.64.40"),
                                )
                            ),
                            searchQuery = nameToUsePrefix
                        )
                    )

                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals(nameToUse, output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.40", output.appsFound.items.first().metadata.version)
                    }
                }

                case("multiple versions - newest not public - user") {
                    val nameToUsePrefix = "test-app-${Random.nextLong()}"
                    val nameToUse = "$nameToUsePrefix-foobar"

                    input(
                        In(
                            userIsAdmin = false,
                            toolsToCreate = listOf(
                                NameAndVersion(
                                    nameToUse,
                                    "1.64.2"
                                ),
                                NameAndVersion(
                                    nameToUse,
                                    "1.64.40"
                                )
                            ),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion(nameToUse, "1.64.2")
                                ),
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.40"),
                                    public = false,
                                    tool = NameAndVersion(nameToUse, "1.64.40"),
                                )
                            ),
                            searchQuery = nameToUsePrefix
                        )
                    )

                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals(nameToUse, output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.2", output.appsFound.items.first().metadata.version)
                    }
                }

                case("multiple versions - newest not public - admin") {
                    val nameToUsePrefix = "test-app-${Random.nextLong()}"
                    val nameToUse = "$nameToUsePrefix-foobar"

                    input(
                        In(
                            userIsAdmin = true,
                            toolsToCreate = listOf(
                                NameAndVersion(
                                    nameToUse,
                                    "1.64.2"
                                ),
                                NameAndVersion(
                                    nameToUse,
                                    "1.64.40"
                                )
                            ),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion(nameToUse, "1.64.2")
                                ),
                                AppCreateInfo(
                                    app = NameAndVersion(nameToUse, "1.64.40"),
                                    public = false,
                                    tool = NameAndVersion(nameToUse, "1.64.40"),
                                )
                            ),
                            searchQuery = nameToUsePrefix
                        )
                    )

                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals(nameToUse, output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.40", output.appsFound.items.first().metadata.version)
                    }
                }
            }
        }
    }
}
