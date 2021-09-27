package dk.sdu.cloud

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.activity.api.Activity
import dk.sdu.cloud.alerting.api.Alerting
import dk.sdu.cloud.app.kubernetes.api.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.audit.ingestion.api.Auditing
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.avatar.api.AvatarDescriptions
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.IngoingRequestInterceptor
import dk.sdu.cloud.calls.server.OutgoingCallResponse
import dk.sdu.cloud.elastic.management.api.ElasticManagement
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.api.UCloudFileCollections
import dk.sdu.cloud.file.ucloud.api.UCloudFiles
import dk.sdu.cloud.file.ucloud.api.UCloudShares
import dk.sdu.cloud.grant.api.Gifts
import dk.sdu.cloud.grant.api.Grants
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.micro.PlaceholderServiceDescription
import dk.sdu.cloud.micro.ServiceRegistry
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.news.api.News
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.password.reset.api.PasswordResetDescriptions
import dk.sdu.cloud.project.api.ProjectGroups
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.favorite.api.ProjectFavorites
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.redis.cleaner.api.RedisCleaner
import dk.sdu.cloud.slack.api.SlackDescriptions
import dk.sdu.cloud.support.api.SupportDescriptions
import dk.sdu.cloud.task.api.Tasks
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

private fun makeSureEverythingIsLoaded() {
    val reg = ServiceRegistry(arrayOf("--dev"), PlaceholderServiceDescription)
    val knownCalls = ArrayList<CallDescription<*, *, *>>()
    reg.rootMicro.server.attachRequestInterceptor(object : IngoingRequestInterceptor<HttpCall, HttpCall.Companion> {
        override val companion = HttpCall.Companion
        override fun addCallListenerForCall(call: CallDescription<*, *, *>) {
            knownCalls.add(call)
        }

        override suspend fun <R : Any> parseRequest(ctx: HttpCall, call: CallDescription<R, *, *>): R {
            error("Will not parse")
        }

        override suspend fun <R : Any, S : Any, E : Any> produceResponse(
            ctx: HttpCall,
            call: CallDescription<R, S, E>,
            callResult: OutgoingCallResponse<S, E>,
        ) {
            error("Will not respond")
        }
    })

    services.forEach { objectInstance ->
        try {
            Launcher.log.trace("Registering ${objectInstance.javaClass.canonicalName}")
            reg.register(objectInstance)
        } catch (ex: Throwable) {
            Launcher.log.error("Caught error: ${ex.stackTraceToString()}")
        }
    }

    reg.start(wait = false)
}

sealed class Chapter {
    abstract val title: String
    abstract var path: List<Chapter.Node>

    data class Node(override val title: String, val children: List<Chapter>) : Chapter() {
        override var path: List<Chapter.Node> = emptyList()
    }
    data class Feature(override val title: String, val container: CallDescriptionContainer) : Chapter() {
        override var path: List<Chapter.Node> = emptyList()
    }
}

fun Chapter.addPaths(path: List<Chapter.Node> = emptyList()) {
    this.path = path
    if (this !is Chapter.Node) return
    val newPath = path + listOf(this)
    children.forEach { it.addPaths(newPath) }
}

fun generateCode() {
//    makeSureEverythingIsLoaded()

    val structure = Chapter.Node(
        "UCloud Developer Guide",
        listOf(
            Chapter.Node(
                "Accounting And Project Management",
                listOf(
                    Chapter.Node(
                        "Project Management",
                        listOf(
                            Chapter.Feature("Projects", Projects),
                            Chapter.Feature("Members", ProjectMembers),
                            Chapter.Feature("Groups", ProjectGroups),
                            Chapter.Feature("Favorites", ProjectFavorites)
                        )
                    ),
                    Chapter.Feature("Providers", Providers),
                    Chapter.Feature("Products", Products),
                    Chapter.Node(
                        "Accounting",
                        listOf(
                            Chapter.Feature("Wallets", Wallets),
                            Chapter.Feature("Allocations", Accounting),
                            Chapter.Feature("Visualization of Usage", Visualization)
                        )
                    ),
                    Chapter.Node(
                        "Grants",
                        listOf(
                            Chapter.Feature("Allocation Process", Grants),
                            Chapter.Feature("Gifts", Gifts)
                        )
                    )
                )
            ),
            Chapter.Node(
                "Orchestration of Resources",
                listOf(
                    Chapter.Node(
                        "Storage",
                        listOf(
                            Chapter.Feature("Drives (FileCollection)", FileCollections),
                            Chapter.Feature("Files", Files),
                            Chapter.Feature("Shares", Shares),
                            Chapter.Node(
                                "Metadata",
                                listOf(
                                    Chapter.Feature("Templates", FileMetadataTemplateNamespaces),
                                    Chapter.Feature("Documents", FileMetadata)
                                )
                            ),
                            Chapter.Node(
                                "Provider APIs",
                                listOf(
                                    Chapter.Node(
                                        "Drives (FileCollection)",
                                        listOf(
                                            Chapter.Feature(
                                                "Ingoing API",
                                                FileCollectionsProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "Outgoing API",
                                                FileCollectionsControl
                                            )
                                        )
                                    ),
                                    Chapter.Node(
                                        "Files",
                                        listOf(
                                            Chapter.Feature(
                                                "Ingoing API",
                                                FilesProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "Outgoing API",
                                                FilesControl
                                            )
                                        )
                                    ),
                                    Chapter.Feature(
                                        "Upload Protocol",
                                        ChunkedUploadProtocol(PROVIDER_ID_PLACEHOLDER, "/placeholder")
                                    ),
                                    Chapter.Node(
                                        "Shares",
                                        listOf(
                                            Chapter.Feature(
                                                "Ingoing API",
                                                SharesProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
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
                        "Compute",
                        listOf(
                            Chapter.Node(
                                "Application Store",
                                listOf(
                                    Chapter.Feature("Tools", ToolStore),
                                    Chapter.Feature("Applications", AppStore)
                                )
                            ),
                            Chapter.Feature("Jobs", Jobs),
                            Chapter.Feature("Public IPs (NetworkIP)", NetworkIPs),
                            Chapter.Feature("Public Links (Ingress)", Ingresses),
                            Chapter.Feature("Software Licenses", Licenses),
                            Chapter.Node(
                                "Provider APIs",
                                listOf(
                                    Chapter.Node(
                                        "Jobs",
                                        listOf(
                                            Chapter.Feature(
                                                "Ingoing API",
                                                JobsProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "Outgoing API",
                                                JobsControl
                                            )
                                        )
                                    ),
                                    Chapter.Feature("Shells", Shells(PROVIDER_ID_PLACEHOLDER)),
                                    Chapter.Node(
                                        "Public IPs (NetworkIP)",
                                        listOf(
                                            Chapter.Feature(
                                                "Ingoing API",
                                                NetworkIPProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "Outgoing API",
                                                NetworkIPControl
                                            )
                                        )
                                    ),
                                    Chapter.Node(
                                        "Public Links (Ingress)",
                                        listOf(
                                            Chapter.Feature(
                                                "Ingoing API",
                                                IngressProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
                                                "Outgoing API",
                                                IngressControl
                                            )
                                        )
                                    ),
                                    Chapter.Node(
                                        "Software Licenses",
                                        listOf(
                                            Chapter.Feature(
                                                "Ingoing API",
                                                LicenseProvider(PROVIDER_ID_PLACEHOLDER)
                                            ),
                                            Chapter.Feature(
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
                "Core",
                listOf(
                    Chapter.Node(
                        "Users",
                        listOf(
                            Chapter.Feature("User Creation", UserDescriptions),
                            Chapter.Node(
                                "Authentication",
                                listOf(
                                    Chapter.Feature("Users", AuthDescriptions),
                                    Chapter.Feature("Providers", AuthProviders),
                                    Chapter.Feature("Password Reset", PasswordResetDescriptions)
                                )
                            ),
                            Chapter.Feature("SLAs", ServiceLicenseAgreement),
                            Chapter.Feature("2FA", TwoFactorAuthDescriptions),
                            Chapter.Feature("Avatars", AvatarDescriptions),
                        )
                    ),
                    Chapter.Node(
                        "Monitoring and Alerting",
                        listOf(
                            Chapter.Feature("Auditing", Auditing),
                            Chapter.Feature("Alerting", Alerting),
                            Chapter.Feature("Activity", Activity),
                            Chapter.Node(
                                "Scripts",
                                listOf(
                                    Chapter.Feature("Redis Cleanup", RedisCleaner),
                                    Chapter.Feature("Elastic Cleanup", ElasticManagement)
                                )
                            )
                        )
                    ),
                    Chapter.Node(
                        "Communication",
                        listOf(
                            Chapter.Feature("News", News),
                            Chapter.Feature("Notifications", NotificationDescriptions),
                            Chapter.Feature("Tasks", Tasks),
                            Chapter.Feature("Support", SupportDescriptions),
                            Chapter.Feature("Slack", SlackDescriptions),
                            Chapter.Feature("Mail", MailDescriptions),
                        )
                    )
                )
            ),
            Chapter.Node(
                "Built-in Provider",
                listOf(
                    Chapter.Node(
                        "UCloud/Storage",
                        listOf(
                            Chapter.Feature("File Collections", UCloudFileCollections),
                            Chapter.Feature("Files", UCloudFiles),
                            Chapter.Feature("Shares", UCloudShares)
                        )
                    ),
                    Chapter.Node(
                        "UCloud/Compute",
                        listOf(
                            Chapter.Feature("Jobs", KubernetesCompute),
                            Chapter.Feature("Public Links (Ingress)", KubernetesIngresses),
                            Chapter.Node(
                                "Public IPs (NetworkIP)",
                                listOf(
                                    Chapter.Feature("Feature", KubernetesNetworkIP),
                                    Chapter.Feature("Maintenance", KubernetesNetworkIPMaintenance)
                                )
                            ),
                            Chapter.Node(
                                "Software Licenses",
                                listOf(
                                    Chapter.Feature("Feature", KubernetesLicenses),
                                    Chapter.Feature("Maintenance", KubernetesLicenseMaintenance)
                                )
                            ),
                            Chapter.Feature("Maintenance", Maintenance)
                        )
                    )
                )
            )
        )
    )
    structure.addPaths()

    var previousSection: Chapter? = null
    val stack = LinkedList<Chapter?>(listOf(structure))
    val types = LinkedHashMap<String, GeneratedType>()

    while (true) {
        val chapter = stack.pollFirst() ?: break
        when (chapter) {
            is Chapter.Feature -> {
                chapter.container.documentation()
                val nextSection = stack.peek()
                val calls = generateCalls(chapter.container, types)

                generateMarkdown(
                    previousSection,
                    nextSection,
                    chapter.path,
                    types,
                    calls,
                    chapter.title,
                    chapter.container,
                )

                generateTypeScriptCode(types, calls, chapter.title, chapter.container)
            }

            is Chapter.Node -> {
                for (child in chapter.children.reversed()) {
                    stack.addFirst(child)
                }
            }
        }
        previousSection = chapter
    }
}
