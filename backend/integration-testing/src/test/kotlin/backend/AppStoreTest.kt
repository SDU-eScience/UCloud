package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.Role
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.db
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlin.test.assertEquals

suspend fun createTool(name: String, version: String, timeCreatedInSeconds: Long) {
    db.withSession { session ->
        session.sendPreparedStatement(
            {
                setParameter("toolName", name)
                setParameter("toolVersion", version)
                setParameter("time", timeCreatedInSeconds)
            },
            """      
                INSERT INTO app_store.tools (name, version, created_at, modified_at, original_document, owner, tool) 
                VALUES (:toolName, :toolVersion, to_timestamp(:time),  to_timestamp(:time), 'doc', 
                'EmilianoMolinaro#3798', 
                '{"info": {"name": "coder-cuda", "version": "1.64.2"}, "image": "dreg.cloud.sdu.dk/ucloud-apps/coder-cuda", "title": "Coder CUDA", "authors": ["coder.com"], "backend": "DOCKER", "license": "MIT", "container": "dreg.cloud.sdu.dk/ucloud-apps/coder-cuda", "description": "Run Visual Studio code on UCloud and access it through your browser. For more information, check [here](https://coder.com).\n", "requiredModules": [], "supportedProviders": null, "defaultNumberOfNodes": 1, "defaultTimeAllocation": {"hours": 1, "minutes": 0, "seconds": 0}}'
                );
            """
        )
    }
}

suspend fun createApp(name: String, version: String, toolName: String, toolVersion: String, timeCreatedInSeconds: Long, isPublic: Boolean) {
    db.withSession { session ->
        session.sendPreparedStatement(
            {
                setParameter("appName", name)
                setParameter("appVersion", version)
                setParameter("toolName", toolName)
                setParameter("toolVersion", toolVersion)
                setParameter("time", timeCreatedInSeconds)
                setParameter("ispublic", isPublic)
            },
            """
                INSERT INTO app_store.applications (name, version, application, created_at, modified_at, 
                original_document, owner, tool_name, tool_version, authors, tags, title, description,
                 website, is_public) 
                VALUES (:appName, :appVersion, 
                '{"vnc": null, "web": {"port": 8080}, "tool": {"name": "coder-cuda", "tool": null, "version": "1.64.2"}, "container": {"runAsRoot": true, "runAsRealUser": false, "changeWorkingDirectory": true}, "invocation": [{"type": "word", "word": "startup.sh"}, {"type": "var", "prefixGlobal": "-d ", "suffixGlobal": "", "variableNames": ["requirements"], "prefixVariable": "", "suffixVariable": "", "isPrefixVariablePartOfArg": false, "isSuffixVariablePartOfArg": false}, {"type": "var", "prefixGlobal": "-m ", "suffixGlobal": "", "variableNames": ["m_var"], "prefixVariable": "", "suffixVariable": "", "isPrefixVariablePartOfArg": false, "isSuffixVariablePartOfArg": false}], "parameters": [{"name": "requirements", "type": "input_file", "title": "Dependencies", "optional": true, "description": "Install additional dependencies. File format: Bash script (.sh)\n", "defaultValue": null}, {"name": "m_var", "type": "input_directory", "title": "Modules path", "optional": true, "description": "Import environment modules folder\n", "defaultValue": null}], "environment": null, "allowPublicIp": false, "allowMultiNode": false, "fileExtensions": [".c", ".cu", ".cuh", ".cc", ".c++", ".cpp", ".cp", ".C", ".CPP", ".cxx", ".h", ".hpp", ".ii"], "licenseServers": [], "allowPublicLink": true, "applicationType": "WEB", "outputFileGlobs": ["*", "stdout.txt", "stderr.txt"], "allowAdditionalPeers": null, "allowAdditionalMounts": null}', 
                 to_timestamp(:time), 
                 to_timestamp(:time), 
                null, 'EmilianoMolinaro#3798', 
                :toolName, :toolVersion, '["coder.com"]', null, 'Coder CUDA', 
                'Run Visual Studio Code on UCloud and access it through your browser. For more information, check [here](https://coder.com).', 
                'https://docs.cloud.sdu.dk/Apps/coder.html', :ispublic);
            """
        )
    }
}

class AppStoreTest: IntegrationTest() {
    override fun defineTests() {
        /*testFilter = { title, subtitle ->
            title == "simple search tests" && subtitle == "multiple versions - newest not public - user"
        }*/
        run {
            data class AppCreateInfo(
                val app: NameAndVersion,
                val public: Boolean,
                val tool: NameAndVersion,
                // default is 1.april 2022
                val timeCreatedInSeconds: Long = 1648801237
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
                        createTool(it.name, it.version, 1648801237)
                    }
                    input.appsToCreate.forEach {
                        createApp(it.app.name, it.app.version, toolName = it.tool.name, toolVersion = it.tool.version, it.timeCreatedInSeconds, it.public)
                    }

                    val user =
                        if (input.userIsAdmin) {
                            createUser("AdminUser", role = Role.ADMIN)
                        } else {
                            createUser("user")
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
                    input(
                        In(
                            userIsAdmin = false,
                            toolsToCreate = listOf(NameAndVersion("coder-cuda", "1.64.2")),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion("coder-cuda", "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion("coder-cuda", "1.64.2")
                                )
                            ),
                            searchQuery = "cuda"
                        )
                    )
                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals("coder-cuda", output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.2", output.appsFound.items.first().metadata.version)
                    }
                }
                case("single version - public - admin") {
                    input(
                        In(
                            userIsAdmin = true,
                            toolsToCreate = listOf(NameAndVersion("coder-cuda", "1.64.2")),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion("coder-cuda", "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion("coder-cuda", "1.64.2")
                                )
                            ),
                            searchQuery = "cuda"
                        )
                    )
                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals("coder-cuda", output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.2", output.appsFound.items.first().metadata.version)
                    }
                }
                case("single version - non public - user") {
                    input(
                        In(
                            userIsAdmin = false,
                            toolsToCreate = listOf(NameAndVersion("coder-cuda", "1.64.2")),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion("coder-cuda", "1.64.2"),
                                    public = false,
                                    tool = NameAndVersion("coder-cuda", "1.64.2")
                                )
                            ),
                            searchQuery = "cuda"                        )
                    )
                    check {
                        assertEquals(0, output.appsFound.itemsInTotal)
                    }
                }
                case("single version - non public - admin") {
                    input(
                        In(
                            userIsAdmin = true,
                            toolsToCreate = kotlin.collections.listOf(
                                dk.sdu.cloud.app.store.api.NameAndVersion(
                                    "coder-cuda",
                                    "1.64.2"
                                )
                            ),
                            appsToCreate = kotlin.collections.listOf(
                                AppCreateInfo(
                                    app = dk.sdu.cloud.app.store.api.NameAndVersion("coder-cuda", "1.64.2"),
                                    public = false,
                                    tool = dk.sdu.cloud.app.store.api.NameAndVersion("coder-cuda", "1.64.2")
                                )
                            ),
                            searchQuery = "cuda"                        )
                    )
                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals("coder-cuda", output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.2", output.appsFound.items.first().metadata.version)
                    }
                }


                case("multiple versions - public - user") {
                    input(
                        In(
                            userIsAdmin = false,
                            toolsToCreate = listOf(
                                NameAndVersion(
                                    "coder-cuda",
                                    "1.64.2"
                                ),
                                NameAndVersion(
                                    "coder-cuda",
                                    "1.64.40"
                                )
                            ),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion("coder-cuda", "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion("coder-cuda", "1.64.2")
                                ),
                                AppCreateInfo(
                                    app = NameAndVersion("coder-cuda", "1.64.40"),
                                    public = true,
                                    tool = NameAndVersion("coder-cuda", "1.64.40"),
                                    timeCreatedInSeconds = 1648887637
                                )
                            ),
                            searchQuery = "cuda"
                        )
                    )
                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals("coder-cuda", output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.40", output.appsFound.items.first().metadata.version)
                    }
                }
                case("multiple versions - newest not public - user") {
                    input(
                        In(
                            userIsAdmin = false,
                            toolsToCreate = listOf(
                                NameAndVersion(
                                    "coder-cuda",
                                    "1.64.2"
                                ),
                                NameAndVersion(
                                    "coder-cuda",
                                    "1.64.40"
                                )
                            ),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion("coder-cuda", "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion("coder-cuda", "1.64.2")
                                ),
                                AppCreateInfo(
                                    app = NameAndVersion("coder-cuda", "1.64.40"),
                                    public = false,
                                    tool = NameAndVersion("coder-cuda", "1.64.40"),
                                    timeCreatedInSeconds = 1648887637
                                )
                            ),
                            searchQuery = "cuda"                        )
                    )
                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals("coder-cuda", output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.2", output.appsFound.items.first().metadata.version)
                    }
                }
                case("multiple versions - newest not public - admin") {
                    input(
                        In(
                            userIsAdmin = true,
                            toolsToCreate = listOf(
                                NameAndVersion(
                                    "coder-cuda",
                                    "1.64.2"
                                ),
                                NameAndVersion(
                                    "coder-cuda",
                                    "1.64.40"
                                )
                            ),
                            appsToCreate = listOf(
                                AppCreateInfo(
                                    app = NameAndVersion("coder-cuda", "1.64.2"),
                                    public = true,
                                    tool = NameAndVersion("coder-cuda", "1.64.2")
                                ),
                                AppCreateInfo(
                                    app = NameAndVersion("coder-cuda", "1.64.40"),
                                    public = false,
                                    tool = NameAndVersion("coder-cuda", "1.64.40"),
                                    timeCreatedInSeconds = 1648887637
                                )
                            ),
                            searchQuery = "cuda"
                        )
                    )
                    check {
                        assertEquals(1, output.appsFound.itemsInTotal)
                        assertEquals("coder-cuda", output.appsFound.items.first().metadata.name)
                        assertEquals("1.64.40", output.appsFound.items.first().metadata.version)
                    }
                }
            }
        }
    }
}
