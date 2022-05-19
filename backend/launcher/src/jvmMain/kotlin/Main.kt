package dk.sdu.cloud

import dk.sdu.cloud.accounting.AccountingService
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.projects.*
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.alerting.AlertingService
import dk.sdu.cloud.app.kubernetes.AppKubernetesService
import dk.sdu.cloud.app.orchestrator.AppOrchestratorService
import dk.sdu.cloud.app.store.AppStoreService
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.CreateTagsRequest
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.audit.ingestion.AuditIngestionService
import dk.sdu.cloud.auth.AuthService
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.avatar.AvatarService
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.contact.book.ContactBookService
import dk.sdu.cloud.elastic.management.ElasticManagementService
import dk.sdu.cloud.file.orchestrator.FileOrchestratorService
import dk.sdu.cloud.slack.SlackService
import dk.sdu.cloud.file.ucloud.FileUcloudService
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.mail.MailService
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.news.NewsService
import dk.sdu.cloud.notification.NotificationService
import dk.sdu.cloud.password.reset.PasswordResetService
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.provider.api.ProviderIncludeFlags
import dk.sdu.cloud.provider.api.ProviderSpecification
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.support.SupportService
import dk.sdu.cloud.task.TaskService
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.logging.log4j.Level
import java.io.File
import kotlin.system.exitProcess

object Launcher : Loggable {
    override val log = logger()
}

val services = setOf<Service>(
    AccountingService,
    AppOrchestratorService,
    AppStoreService,
    AuditIngestionService,
    AuthService,
    AvatarService,
    ContactBookService,
    ElasticManagementService,
    MailService,
    NewsService,
    NotificationService,
    PasswordResetService,
    FileOrchestratorService,
    FileUcloudService,
    SupportService,
    AppKubernetesService,
    TaskService,
    AlertingService,
    SlackService
)

enum class LauncherPreset(val flag: String, val serviceFilter: (Service) -> Boolean) {
    Full("full", { true }),

    FullNoProviders("no-providers", { it != AppKubernetesService && it != FileUcloudService }),

    Core("core", { svc ->
        when (svc) {
            AuditIngestionService,
            AuthService,
            AvatarService,
            ContactBookService,
            ElasticManagementService,
            MailService,
            NewsService,
            NotificationService,
            AlertingService,
            PasswordResetService,
            SupportService,
            TaskService -> true
            else -> false
        }
    }),

    AccountingAndProjectManagement("apm", { svc ->
        when (svc) {
            AccountingService -> true
            else -> false
        }
    }),

    Orchestrators("orchestrators", { svc ->
        when (svc) {
            AppOrchestratorService,
            FileOrchestratorService -> true
            else -> false
        }
    }),

    Providers("providers", { svc ->
        when (svc) {
            AppKubernetesService,
            FileUcloudService -> true
            else -> false
        }
    }),
}

suspend fun main(args: Array<String>) {
    val loadedConfig = Micro().apply {
        commandLineArguments = args.toList()
        isEmbeddedService = false
        serviceDescription = PlaceholderServiceDescription

        install(ConfigurationFeature)
    }.configuration

    if (args.contains("--run-script") && args.contains("spring-gen")) {
        generateSpringMvcCode()
        exitProcess(0)
    }

    if (args.contains("--run-script") && args.contains("api-gen")) {
        generateCode()
        exitProcess(0)
    }

    if (args.contains("--run-script") && args.contains("migrate-db")) {
        val reg = ServiceRegistry(args, PlaceholderServiceDescription)
        reg.rootMicro.install(DatabaseConfigurationFeature)
        reg.rootMicro.install(FlywayFeature)
        reg.rootMicro.databaseConfig.migrateAll()
        exitProcess(0)
    }

    val presetArg = args.getOrNull(0)?.takeIf { !it.startsWith("--") }
    val preset = if (presetArg == null) {
        LauncherPreset.Full
    } else {
        LauncherPreset.values().find { it.flag == presetArg }
            ?: error(
                "Unknown preset: $presetArg (available options: ${
                    LauncherPreset.values().joinToString(", ") { it.flag }
                })")
    }

    val shouldInstall = loadedConfig.tree.elements().asSequence().toList().isEmpty() ||
            loadedConfig.requestChunkAtOrNull<Boolean>("installing") == true

    if (args.contains("--dev") && shouldInstall && preset == LauncherPreset.Full) {
        println("UCloud is now ready to be installed!")
        println("Visit http://localhost:8080/i in your browser")
        runInstaller(loadedConfig.configDirs.first())
        exitProcess(0)
    }

    val reg = ServiceRegistry(args, PlaceholderServiceDescription)

    services.filter { preset.serviceFilter(it) }.forEach { objectInstance ->
        try {
            Launcher.log.trace("Registering ${objectInstance.javaClass.canonicalName}")
            reg.register(objectInstance)
        } catch (ex: Throwable) {
            Launcher.log.error("Caught error: ${ex.stackTraceToString()}")
        }
    }

    reg.rootMicro.feature(LogFeature).configureLevels(
        mapOf(
            // Don't display same information twice
            "dk.sdu.cloud.calls.client.OutgoingHttpRequestInterceptor" to Level.INFO
        )
    )

    if (loadedConfig.requestChunkAtOrNull<Boolean>("postInstalling") == true) {
        GlobalScope.launch {
            val configDir = loadedConfig.configDirs.first()
            while (!ucloudIsReady.get()) {
                delay(100)
            }

            val m = Micro(reg.rootMicro)
            m.install(AuthenticatorFeature)
            val client = m.authenticator.authenticateClient(OutgoingHttpCall)
            val newUser = UserDescriptions.createNewUser.call(
                listOf(
                    CreateSingleUserRequest(
                        "user",
                        "mypassword",
                        role = Role.ADMIN,
                        email = "user@localhost"
                    )
                ),
                client
            ).orThrow()

            val userClient = RefreshingJWTAuthenticator(
                reg.rootMicro.client,
                JwtRefresher.Normal(newUser[0].refreshToken, OutgoingHttpCall)
            ).authenticateClient(OutgoingHttpCall)

            val project = Projects.create.call(
                CreateProjectRequest(
                    "UCloud",
                    principalInvestigator = "user"
                ),
                client
            ).orThrow().id

            ProjectEnabled.setEnabledStatus.call(
                bulkRequestOf(SetEnabledStatusRequest(project, true)),
                userClient
            ).orThrow()

            GrantSettings.uploadRequestSettings.call(
                bulkRequestOf(UploadRequestSettingsRequest(
                    automaticApproval = AutomaticApprovalSettings(
                        from = emptyList(),
                        maxResources = emptyList()
                    ),
                    allowRequestsFrom = listOf<UserCriteria>(UserCriteria.Anyone()),
                    excludeRequestsFrom = emptyList(),
                    projectId = project
                )),
                userClient.withProject(project)
            )

            val providerId = "ucloud"
            val createdId = Providers.create.call(
                bulkRequestOf(
                    ProviderSpecification(
                        providerId,
                        "localhost",
                        false,
                        8080
                    )
                ),
                userClient.withProject(project)
            ).orThrow().responses.singleOrNull() ?: error("Bad response from Providers.create")

            val provider = Providers.retrieve.call(
                ResourceRetrieveRequest(ProviderIncludeFlags(), createdId.id),
                userClient
            ).orThrow()

            File(configDir, "ucloud-compute-config.yaml").writeText(
                """
                    app:
                        kubernetes:
                            providerRefreshToken: ${provider.refreshToken}
                            ucloudCertificate: ${provider.publicKey}
                """.trimIndent()
            )

            File(configDir, "ucloud-storage-config.yaml").writeText(
                """
                    files:
                        ucloud:
                            providerRefreshToken: ${provider.refreshToken}
                            ucloudCertificate: ${provider.publicKey}
                """.trimIndent()
            )

            Products.create.call(
                bulkRequestOf(
                    Product.Compute(
                        "u1-standard-1",
                        1000L,
                        ProductCategoryId("u1-standard", providerId),
                        cpu = 1,
                        memoryInGigs = 1,
                        gpu = 0,
                        unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE,
                        freeToUse = false,
                        description = "An example product for development use",
                    ),
                    Product.Compute(
                        "syncthing",
                        1L,
                        ProductCategoryId("syncthing", providerId),
                        cpu = 1,
                        memoryInGigs = 1,
                        gpu = 0,
                        unitOfPrice = ProductPriceUnit.PER_UNIT,
                        freeToUse = true,
                        description = "Product used for file synchronization",
                    ),
                    Product.Storage(
                        "u1-cephfs",
                        1L,
                        ProductCategoryId("u1-cephfs", providerId),
                        unitOfPrice = ProductPriceUnit.PER_UNIT,
                        freeToUse = false,
                        description = "An example product for development use",
                        chargeType = ChargeType.DIFFERENTIAL_QUOTA
                    ),
                    Product.Storage(
                        "home",
                        1L,
                        ProductCategoryId("u1-cephfs", providerId),
                        unitOfPrice = ProductPriceUnit.PER_UNIT,
                        freeToUse = false,
                        description = "An example product for development use",
                        chargeType = ChargeType.DIFFERENTIAL_QUOTA
                    ),
                    Product.Storage(
                        "project-home",
                        1L,
                        ProductCategoryId("u1-cephfs", providerId),
                        unitOfPrice = ProductPriceUnit.PER_UNIT,
                        freeToUse = false,
                        description = "An example product for development use",
                        chargeType = ChargeType.DIFFERENTIAL_QUOTA
                    ),
                    Product.Storage(
                        "share",
                        1,
                        ProductCategoryId("u1-cephfs", providerId),
                        "Shares for UCloud (personal workspaces only)",
                        chargeType = ChargeType.DIFFERENTIAL_QUOTA,
                        unitOfPrice = ProductPriceUnit.PER_UNIT
                    )
                ),
                userClient
            ).orThrow()

            Accounting.rootDeposit.call(
                bulkRequestOf(
                    RootDepositRequestItem(
                        ProductCategoryId("u1-cephfs", providerId),
                        WalletOwner.Project(project),
                        1_000_000L,
                        "Root deposit"
                    ),
                    RootDepositRequestItem(
                        ProductCategoryId("u1-standard", providerId),
                        WalletOwner.Project(project),
                        1_000_000L * 1000_000L,
                        "Root deposit"
                    ),
                    RootDepositRequestItem(
                        ProductCategoryId("u1-cephfs", providerId),
                        WalletOwner.User("user"),
                        1_000,
                        "Root deposit"
                    ),
                    RootDepositRequestItem(
                        ProductCategoryId("u1-standard", providerId),
                        WalletOwner.User("user"),
                        1_000_000L * 1000_000L,
                        "Root deposit"
                    ),
                ),
                client
            ).orThrow()



            ToolStore.create.call(
                Unit,
                client.withHttpBody(
                    //language=yaml
                    """
                        ---
                        tool: v1

                        title: Alpine
                        name: alpine
                        version: 1

                        container: alpine:3

                        authors:
                        - Dan
                          
                        defaultTimeAllocation:
                          hours: 1
                          minutes: 0
                          seconds: 0

                        description: All
                                   
                        defaultNumberOfNodes: 1 
                        defaultTasksPerNode: 1

                        backend: DOCKER                        
                    """.trimIndent(),
                    ContentType("text", "yaml")
                )
            ).orThrow()

            AppStore.create.call(
                Unit,
                client.withHttpBody(
                    //language=yaml
                    """
                        application: v1

                        title: Alpine
                        name: alpine
                        version: 3

                        applicationType: BATCH

                        allowMultiNode: true

                        tool:
                          name: alpine
                          version: 1

                        authors:
                        - Dan

                        container:
                          runAsRoot: true
                         
                        description: >
                          Alpine!
                         
                        invocation:
                        - sh
                        - -c
                        - >
                          echo "Hello, World!";
                          sleep 2;
                          echo "How are you doing?";
                          sleep 1;
                          echo "This is just me writing some stuff for testing purposes!";
                          sleep 1;
                          seq 0 7200 | xargs -n 1 -I _ sh -c 'echo _; sleep 1';

                        outputFileGlobs:
                          - "*"
                        
                    """.trimIndent(),
                    ContentType("text", "yaml")
                )
            ).orThrow()

            AppStore.createTag.call(
                CreateTagsRequest(
                    listOf("Featured"),
                    "alpine"
                ),
                client
            ).orThrow()

            File(configDir, installerFile).delete()

            repeat(30) { println() }
            println("Please restart UCloud if this doesn't happen automatically.")
            println("After the restart, UCloud will be available on: http://localhost:9000 (If using docker-compose)")
            exitProcess(0)
        }
    }

    reg.rootMicro.feature(ServerFeature).ktorApplicationEngine!!.application.routing {
        get("/i") {
            call.respondRedirect("http://localhost:9000/app", permanent = false)
        }
    }

    reg.start()
}
