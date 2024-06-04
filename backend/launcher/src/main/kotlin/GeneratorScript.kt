package dk.sdu.cloud

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.alerting.api.Alerting
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.audit.ingestion.api.Auditing
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.ServiceLicenseAgreement
import dk.sdu.cloud.auth.api.TwoFactorAuthDescriptions
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.avatar.api.AvatarDescriptions
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.elastic.management.api.ElasticManagement
import dk.sdu.cloud.file.orchestrator.api.ChunkedUploadProtocol
import dk.sdu.cloud.file.orchestrator.api.FileCollections
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.file.orchestrator.api.FileMetadata
import dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplateNamespaces
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesControl
import dk.sdu.cloud.file.orchestrator.api.FilesProvider
import dk.sdu.cloud.file.orchestrator.api.Shares
import dk.sdu.cloud.file.orchestrator.api.SharesControl
import dk.sdu.cloud.file.orchestrator.api.SharesProvider
import dk.sdu.cloud.grant.api.Gifts
import dk.sdu.cloud.grant.api.GrantsV2
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.news.api.News
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.password.reset.api.PasswordResetDescriptions
import dk.sdu.cloud.project.api.ProjectGroups
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.favorite.api.ProjectFavorites
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.provider.api.ResourceControl
import dk.sdu.cloud.provider.api.ResourceProvider
import dk.sdu.cloud.provider.api.Resources
import dk.sdu.cloud.redis.cleaner.api.RedisCleaner
import dk.sdu.cloud.slack.api.SlackDescriptions
import dk.sdu.cloud.support.api.SupportDescriptions
import dk.sdu.cloud.task.api.Tasks
import java.util.*

sealed class Chapter {
    abstract val id: String
    abstract val title: String
    abstract var path: List<Chapter.Node>

    data class Node(
        override val id: String,
        override val title: String,
        val children: List<Chapter>
    ) : Chapter() {
        override var path: List<Chapter.Node> = emptyList()
    }

    data class Feature(
        override val id: String,
        override val title: String,
        val container: CallDescriptionContainer
    ) : Chapter() {
        override var path: List<Chapter.Node> = emptyList()
    }

    data class ExternalMarkdown(
        override val id: String,
        override val title: String,
        val externalFile: String,
    ) : Chapter() {
        override var path: List<Chapter.Node> = emptyList()
    }
}

fun Chapter.addPaths(path: List<Chapter.Node> = emptyList()) {
    this.path = path
    if (this !is Chapter.Node) return
    val newPath = path + listOf(this)
    children.forEach { it.addPaths(newPath) }
}

fun Chapter.previous(): Chapter? {
    val parent = path.lastOrNull()
    return if (parent != null) {
        val indexOfSelf = parent.children.indexOf(this)
        if (indexOfSelf > 0) {
            parent.children[indexOfSelf - 1]
        } else {
            val previousSection = parent.previous()
            if (previousSection is Chapter.Node) {
                previousSection.children.lastOrNull() ?: previousSection
            } else {
                previousSection
            }
        }
    } else {
        null
    }
}

@OptIn(UCloudApiExampleValue::class)
fun generateCode() {
    val structure = Chapter.Node(
        "developer-guide",
        "UCloud Developer Guide",
        listOf(
            Chapter.Node(
                "accounting-and-projects",
                "Accounting and Project Management",
                listOf(
                    Chapter.Feature("projects", "Projects", dk.sdu.cloud.project.api.v2.Projects),
                    Chapter.Feature("providers", "Providers", Providers),
                    Chapter.Feature("products", "Products", ProductsV2),
                    Chapter.Node(
                        "accounting",
                        "Accounting",
                        listOf(
                            Chapter.Feature("allocations", "Accounting", AccountingV2),
                        )
                    ),
                    Chapter.Node(
                        "grants",
                        "Grants",
                        listOf(
                            Chapter.Feature("grants", "Allocation Process", GrantsV2),
                            Chapter.Feature("gifts", "Gifts", Gifts)
                        )
                    )
                )
            ),
            Chapter.Node(
                "orchestration",
                "Orchestration of Resources",
                listOf(
                    Chapter.Feature("resources", "Introduction to Resources", Resources),
                    Chapter.Node(
                        "storage",
                        "Storage",
                        listOf(
                            Chapter.Feature("filecollections", "Drives (FileCollection)", FileCollections),
                            Chapter.Feature("files", "Files", Files),
                            Chapter.Feature("shares", "Shares", Shares),
                            Chapter.Node(
                                "metadata",
                                "Metadata",
                                listOf(
                                    Chapter.Feature("templates", "Metadata Templates", FileMetadataTemplateNamespaces),
                                    Chapter.Feature("documents", "Metadata Documents", FileMetadata)
                                )
                            ),
                            Chapter.Node(
                                "providers",
                                "Provider APIs",
                                listOf(
                                    Chapter.Feature(
                                        "resources",
                                        "Introduction to Resources API for Providers",
                                        ResourceProvider
                                    ),
                                    Chapter.Feature(
                                        "control",
                                        "Introduction to Resources Control API",
                                        ResourceControl
                                    ),
                                    Chapter.Node(
                                        "drives",
                                        "Drives (FileCollection)",
                                        listOf(
                                            Chapter.Feature(
                                                "ingoing",
                                                "Ingoing API",
                                                FileCollectionsProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "outgoing",
                                                "Outgoing API",
                                                FileCollectionsControl
                                            )
                                        )
                                    ),
                                    Chapter.Node(
                                        "files",
                                        "Files",
                                        listOf(
                                            Chapter.Feature(
                                                "ingoing",
                                                "Ingoing API",
                                                FilesProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "outgoing",
                                                "Outgoing API",
                                                FilesControl
                                            )
                                        )
                                    ),
                                    Chapter.Feature(
                                        "upload",
                                        "Upload Protocol",
                                        ChunkedUploadProtocol(PROVIDER_ID_PLACEHOLDER, "/placeholder")
                                    ),
                                    Chapter.Node(
                                        "shares",
                                        "Shares",
                                        listOf(
                                            Chapter.Feature(
                                                "ingoing",
                                                "Ingoing API",
                                                SharesProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "outgoing",
                                                "Outgoing API",
                                                SharesControl
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    Chapter.Node(
                        "compute",
                        "Compute",
                        listOf(
                            Chapter.Node(
                                "appstore",
                                "Application Store",
                                listOf(
                                    Chapter.Feature("tools", "Tools", ToolStore),
                                    Chapter.Feature("apps", "Applications", AppStore)
                                )
                            ),
                            Chapter.Feature("jobs", "Jobs", Jobs),
                            Chapter.Feature("ips", "Public IPs (NetworkIP)", NetworkIPs),
                            Chapter.Feature("ingress", "Public Links (Ingress)", Ingresses),
                            Chapter.Feature("license", "Software Licenses", Licenses),
                            Chapter.Feature("syncthing", "Syncthing", Syncthing),
                            Chapter.Node(
                                "providers",
                                "Provider APIs",
                                listOf(
                                    Chapter.Node(
                                        "jobs",
                                        "Jobs",
                                        listOf(
                                            Chapter.Feature(
                                                "ingoing",
                                                "Ingoing API",
                                                JobsProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "outgoing",
                                                "Outgoing API",
                                                JobsControl
                                            )
                                        )
                                    ),
                                    Chapter.Feature("shells", "Shells", Shells(PROVIDER_ID_PLACEHOLDER)),
                                    Chapter.Node(
                                        "ips",
                                        "Public IPs (NetworkIP)",
                                        listOf(
                                            Chapter.Feature(
                                                "ingoing",
                                                "Ingoing API",
                                                NetworkIPProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "outgoing",
                                                "Outgoing API",
                                                NetworkIPControl
                                            )
                                        )
                                    ),
                                    Chapter.Node(
                                        "ingress",
                                        "Public Links (Ingress)",
                                        listOf(
                                            Chapter.Feature(
                                                "ingoing",
                                                "Ingoing API",
                                                IngressProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "outgoing",
                                                "Outgoing API",
                                                IngressControl
                                            )
                                        )
                                    ),
                                    Chapter.Node(
                                        "licenses",
                                        "Software Licenses",
                                        listOf(
                                            Chapter.Feature(
                                                "ingoing",
                                                "Ingoing API",
                                                LicenseProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "outgoing",
                                                "Outgoing API",
                                                LicenseControl
                                            )
                                        )
                                    ),
                                )
                            )
                        )
                    )
                )
            ),
            Chapter.Node(
                "development",
                "Developing UCloud",
                listOf(
                    Chapter.ExternalMarkdown("getting-started", "Getting Started", "../service-lib/wiki/getting_started.md"),
                    Chapter.ExternalMarkdown("first-service", "Your first service", "../service-lib/wiki/first_service.md"),
                    Chapter.ExternalMarkdown("architecture", "High-Level Architecture", "../service-lib/wiki/microservice_structure.md"),
                    Chapter.Node("micro", "Micro Library Reference", listOf(
                        Chapter.ExternalMarkdown("features", "Features", "../service-lib/wiki/micro/features.md"),
                        Chapter.ExternalMarkdown("events", "Events", "../service-lib/wiki/micro/events.md"),
                        Chapter.ExternalMarkdown("distributed_locks", "Distributed Locks", "../service-lib/wiki/micro/distributed_locks.md"),
                        Chapter.Node("rpc", "RPC", listOf(
                            Chapter.ExternalMarkdown("intro", "Introduction", "../service-lib/wiki/micro/rpc.md"),
                            Chapter.ExternalMarkdown("rpc_client", "RPC Client", "../service-lib/wiki/micro/rpc_client.md"),
                            Chapter.ExternalMarkdown("rpc_server", "RPC Server", "../service-lib/wiki/micro/rpc_server.md"),
                            Chapter.ExternalMarkdown("rpc_audit", "RPC Audit", "../service-lib/wiki/micro/rpc_audit.md"),
                            Chapter.ExternalMarkdown("rpc_auth", "RPC Auth", "../service-lib/wiki/micro/rpc_auth.md"),
                            Chapter.ExternalMarkdown("rpc_http", "RPC HTTP", "../service-lib/wiki/micro/rpc_http.md"),
                            Chapter.ExternalMarkdown("rpc_websocket", "RPC WebSocket", "../service-lib/wiki/micro/rpc_websocket.md"),
                        )),
                        Chapter.ExternalMarkdown("http", "HTTP", "../service-lib/wiki/micro/http.md"),
                        Chapter.ExternalMarkdown("websockets", "WebSockets", "../service-lib/wiki/micro/websockets.md"),
                        Chapter.ExternalMarkdown("serialization", "Serialization", "../service-lib/wiki/micro/serialization.md"),
                        Chapter.ExternalMarkdown("pagination", "Pagination", "../service-lib/wiki/micro/pagination.md"),
                        Chapter.ExternalMarkdown("postgres", "Postgres", "../service-lib/wiki/micro/postgres.md"),
                        Chapter.ExternalMarkdown("cache", "Cache", "../service-lib/wiki/micro/cache.md"),
                        Chapter.ExternalMarkdown("time", "Time", "../service-lib/wiki/micro/time.md"),
                    )),
                )
            ),
            Chapter.Node(
                "core",
                "Core",
                listOf(
                    Chapter.Feature("types", "Core Types", CoreTypes),
                    Chapter.Feature("api-conventions", "API Conventions", ApiConventions),
                    Chapter.Feature("api-stability", "API Stability", ApiStability),
                    Chapter.Node(
                        "users",
                        "Users",
                        listOf(
                            Chapter.Feature("creation", "User Creation", UserDescriptions),
                            Chapter.Node(
                                "authentication",
                                "Authentication",
                                listOf(
                                    Chapter.Feature("users", "User Authentication", AuthDescriptions),
                                    Chapter.Feature("providers", "Provider Authentication", AuthProviders),
                                    Chapter.Feature("password-reset", "Password Reset", PasswordResetDescriptions)
                                )
                            ),
                            Chapter.Feature("slas", "SLAs", ServiceLicenseAgreement),
                            Chapter.Feature("2fa", "2FA", TwoFactorAuthDescriptions),
                            Chapter.Feature("avatars", "Avatars", AvatarDescriptions),
                        )
                    ),
                    Chapter.Node(
                        "monitoring",
                        "Monitoring, Alerting and Procedures",
                        listOf(
                            Chapter.ExternalMarkdown("introduction", "Introduction to Procedures", "../service-lib/wiki/procedures_intro.md"),
                            Chapter.Feature("auditing", "Auditing", Auditing),
                            Chapter.ExternalMarkdown("auditing-scenario", "Auditing Scenario", "../service-lib/wiki/auditing-scenario.md"),
                            Chapter.ExternalMarkdown("dependencies", "Third-Party Dependencies (Risk Assessment)", "../service-lib/wiki/third_party_dependencies.md"),
                            Chapter.ExternalMarkdown("deployment", "Deployment", "../service-lib/wiki/deployment.md"),
                            Chapter.ExternalMarkdown("jenkins", "Jenkins", "../service-lib/wiki/jenkins.md"),
                            Chapter.ExternalMarkdown("elastic", "ElasticSearch", "../service-lib/wiki/elastic.md"),
                            Chapter.ExternalMarkdown("grafana", "Grafana", "../service-lib/wiki/grafana.md"),
                            Chapter.Feature("alerting", "Alerting", Alerting),
                            Chapter.Node(
                                "scripts",
                                "Management Scripts",
                                listOf(
                                    Chapter.Feature("redis", "Redis Cleanup", RedisCleaner),
                                    Chapter.Feature("elastic", "Elastic Cleanup", ElasticManagement)
                                )
                            ),
                        )
                    ),
                    Chapter.Node(
                        "communication",
                        "Communication",
                        listOf(
                            Chapter.Feature("news", "News", News),
                            Chapter.Feature("notifications", "Notifications", NotificationDescriptions),
                            Chapter.Feature("tasks", "Tasks", Tasks),
                            Chapter.Feature("support", "Support", SupportDescriptions),
                            Chapter.Feature("slack", "Slack", SlackDescriptions),
                            Chapter.Feature("mail", "Mail", MailDescriptions),
                        )
                    )
                )
            ),
            Chapter.Node(
                "legacy",
                "Legacy",
                listOf(
                    Chapter.Node(
                        "projects-legacy",
                        "Projects (Legacy)",
                        listOf(
                            Chapter.Feature("projects", "Projects", Projects),
                            Chapter.Feature("members", "Members", ProjectMembers),
                            Chapter.Feature("groups", "Groups", ProjectGroups),
                            Chapter.Feature("favorites", "Favorites", ProjectFavorites)
                        )
                    )
                )
            ),
        )
    )
    structure.addPaths()

    val stack = LinkedList<Chapter?>(listOf(structure))
    val types = LinkedHashMap<String, GeneratedType>()
    val callsByFeature = HashMap<Chapter.Feature, List<GeneratedRemoteProcedureCall>>()
    val useCases = ArrayList<UseCase>()
    var firstPass = true

    while (true) {
        val chapter = stack.pollFirst()
        if (chapter == null) {
            if (firstPass) {
                stack.add(structure)
                firstPass = false

                var didFail = false
                for ((name, type) in types) {
                    if (type.owner == null && name.startsWith("dk.sdu.cloud.")) {
                        println("$name has no owner. You can fix this by attaching @UCloudApiOwnedBy(XXX::class)")
                        didFail = true
                    }
                }
                if (didFail) break
                continue
            } else {
                break
            }
        }

        val actualPreviousSection = chapter.previous()
        when (chapter) {
            is Chapter.Feature -> {
                if (firstPass) {
                    chapter.container.documentation()
                    callsByFeature[chapter] = generateCalls(chapter.container, types)
                } else {
                    val nextSection = stack.peek()
                    val calls = callsByFeature.getValue(chapter)
                    generateMarkdown(
                        actualPreviousSection,
                        nextSection,
                        chapter.path,
                        types,
                        calls,
                        chapter
                    )

                    useCases.addAll(chapter.container.useCases)
                }
            }

            is Chapter.ExternalMarkdown -> {
                if (!firstPass) {
                    val nextSection = stack.peek()
                    generateExternalMarkdown(
                        actualPreviousSection,
                        nextSection,
                        chapter.path,
                        chapter
                    )
                }
            }

            is Chapter.Node -> {
                for (child in chapter.children.reversed()) {
                    stack.addFirst(child)
                }
                var nextSection = stack.peek()
                while (nextSection is Chapter.Node) {
                    // NOTE(Dan): don't set next section to null if not needed
                    val next = nextSection.children.firstOrNull() ?: break
                    nextSection = next
                }
                generateMarkdownChapterTableOfContents(actualPreviousSection, nextSection, chapter.path, chapter)
                generateSphinxTableOfContents(chapter)
            }
        }
    }
    generateSphinxCalls(callsByFeature.values)
    generateSphinxTypes(types.values)
    generateSphinxExamples(useCases)
    generateFrontendRpcNameTable(callsByFeature.values)
}

const val PROVIDER_ID_PLACEHOLDER = "PROVIDERID"
