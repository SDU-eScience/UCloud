package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class DevImportRequest(
    val endpoint: String,
    val checksum: String
)

@Serializable
data class Project(
    val id: String,
    val title: String
)
typealias ProjectGroup = Project

@Serializable
data class AccessEntity(
    val user: String? = null,
    val project: String? = null,
    val group: String? = null,
) {
    init {
        require(!user.isNullOrBlank() || (!project.isNullOrBlank() && !group.isNullOrBlank())) { "No access entity defined" }
    }
}

@Serializable
data class DetailedAccessEntity(
    val user: String? = null,
    val project: Project? = null,
    val group: ProjectGroup? = null,
) {
    init {
        require(!user.isNullOrBlank() || (project != null && group != null)) { "No access entity defined" }
    }
}

@Serializable
data class EntityWithPermission(
    val entity: AccessEntity,
    val permission: ApplicationAccessRight
)

@Serializable
data class DetailedEntityWithPermission(
    val entity: DetailedAccessEntity,
    val permission: ApplicationAccessRight
)

@Serializable
data class FindApplicationAndOptionalDependencies(
    val appName: String,
    val appVersion: String
)

@Serializable
data class HasPermissionRequest(
    val appName: String,
    val appVersion: String,
    val permission: Set<ApplicationAccessRight>
)

@Serializable
data class UpdateAclRequest(
    val applicationName: String,
    val changes: List<ACLEntryRequest>
) {
    init {
        if (changes.isEmpty()) throw IllegalArgumentException("changes cannot be empty")
        if (changes.size > 1000) throw IllegalArgumentException("Too many new entries")
    }
}

@Serializable
data class IsPublicRequest(
    val applications: List<NameAndVersion>
)

@Serializable
data class IsPublicResponse(
    val public: Map<NameAndVersion, Boolean>
)


@Serializable
data class ListAclRequest(
    val appName: String
)

@Serializable
data class FavoriteRequest(
    val appName: String,
    val appVersion: String
)

@Serializable
data class ACLEntryRequest(
    val entity: AccessEntity,
    val rights: ApplicationAccessRight,
    val revoke: Boolean = false
)

@Serializable
data class SetPublicRequest(
    val appName: String,
    val appVersion: String,
    val public: Boolean
)

@Serializable
data class TagSearchRequest(
    val query: String,
    val excludeTools: String? = null,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest

val TagSearchRequest.tags: List<String> get() = query.split(",")

@Serializable
data class AppSearchRequest(
    val query: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest

@Serializable
data class CreateTagsRequest(
    val tags: List<String>,
    val groupId: Int
)

typealias DeleteTagsRequest = CreateTagsRequest

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
data class UploadApplicationLogoRequest(
    val name: String,
)

@Serializable
data class AdvancedSearchRequest(
    val query: String? = null,
    val tags: List<String>? = null,
    val showAllVersions: Boolean,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest


@Serializable
@UCloudApiOwnedBy(ToolStore::class)
data class ClearLogoRequest(val name: String)
typealias ClearLogoResponse = Unit

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
data class FetchLogoRequest(val name: String)
typealias FetchLogoResponse = Unit

typealias UploadApplicationLogoResponse = Unit

@Serializable
data class FindLatestByToolRequest(
    val tool: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest
typealias FindLatestByToolResponse = Page<Application>

@Serializable
data class DeleteAppRequest(val appName: String, val appVersion: String)
typealias DeleteAppResponse = Unit

@Serializable
data class RetrieveGroupResponse(
    val group: ApplicationGroup,
    val applications: List<ApplicationSummary>
)

@Serializable
data class UpdatePageRequest(
    val page: AppStorePageType
)

@Serializable
enum class AppStorePageType {
    LANDING,
    FULL
}

@Serializable
data class SetGroupRequest(
    val groupId: Int? = null,
    val applicationName: String
)

typealias SetGroupResponse = Unit

@Serializable
data class CreateGroupRequest(
    val title: String
)

typealias CreateGroupResponse = Unit

@Serializable
data class DeleteGroupRequest(
    val id: Int
)

typealias DeleteGroupResponse = Unit

@Serializable
data class UpdateGroupRequest(
    val id: Int,
    val title: String,
    val logo: ByteArray? = null,
    val description: String? = null,
    val defaultApplication: NameAndVersion? = null
)

typealias UpdateGroupResponse = Unit
typealias ListGroupsRequest = Unit

@Serializable
data class RetrieveGroupRequest(
    val id: Int? = null,
    val name: String? = null
)

@Serializable
data class AppStoreSectionsRequest(
    val page: AppStorePageType
)

@Serializable
data class AppStoreSectionsResponse(
    val sections: List<AppStoreSection>
)

@Serializable
data class ApplicationGroup (
    val id: Int,
    val title: String,
    val description: String? = null,
    val defaultApplication: NameAndVersion? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class AppStoreSection (
    val name: String,
    val featured: List<ApplicationGroup>,
    val items: List<ApplicationGroup>
)

@Serializable
data class PageSection(
    val title: String? = null,
    val featured: List<String>,
    val tags: List<String> = emptyList()
)

@UCloudApiExampleValue
fun exampleApplication(
    name: String,
    version: String,
    image: String,
    invocation: List<InvocationParameter>,
    parameters: List<ApplicationParameter>,
    toolBackend: ToolBackend = ToolBackend.DOCKER,
    type: ApplicationType = ApplicationType.BATCH,
    title: String = name.replace("-", " ").replaceFirstChar { it.uppercase() },
    invocationBlock: (ApplicationInvocationDescription) -> ApplicationInvocationDescription = { it }
): Application {
    return Application(
        ApplicationMetadata(
            name,
            version,
            listOf("UCloud"),
            title,
            "An example application",
            public = true,
            group = ApplicationGroup(
                0,
                "Test Group"
            )
        ),
        ApplicationInvocationDescription(
            ToolReference(
                name, version, Tool(
                    "_ucloud",
                    1633329776235,
                    1633329776235,
                    NormalizedToolDescription(
                        NameAndVersion(name, version),
                        defaultNumberOfNodes = 1,
                        defaultTimeAllocation = SimpleDuration(1, 0, 0),
                        requiredModules = emptyList(),
                        authors = listOf("UCloud"),
                        title = title,
                        description = "An example tool",
                        backend = toolBackend,
                        license = "None",
                        image = image
                    )
                )
            ),
            invocation,
            parameters,
            listOf("*"),
            type
        ).let(invocationBlock)
    )
}

@UCloudApiExampleValue
val exampleBatchApplication = exampleApplication(
    "acme-batch",
    "1.0.0",
    "acme/batch:1.0.0",
    listOf(
        WordInvocationParameter("acme-batch"),
        VariableInvocationParameter(
            listOf("debug"),
            prefixGlobal = "--debug "
        ),
        VariableInvocationParameter(
            listOf("value")
        )
    ),
    listOf(
        ApplicationParameter.Bool(
            "debug",
            description = "Should debug be enabled?"
        ),
        ApplicationParameter.Text(
            "value",
            description = "The value for the batch application"
        )
    )
)

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object AppStore : CallDescriptionContainer("hpc.apps") {
    const val baseContext = "/api/hpc/apps/"

    init {
        description = """
Applications specify the input parameters and invocation of a software package.

${ToolStore.sharedIntroduction}

In concrete terms, the ["invocation"]($TYPE_REF_LINK ApplicationInvocationDescription) of an $TYPE_REF Application
covers:

- [Mandatory and optional input parameters.]($TYPE_REF_LINK ApplicationParameter) For example: text and numeric values,
  command-line flags and input files.
- [The command-line invocation, using values from the input parameters.]($TYPE_REF_LINK InvocationParameter)
- [Resources attached to the compute environment.]($TYPE_REF_LINK ApplicationParameter) For example: files, 
  IP addresses and software licenses.
- [An application type]($TYPE_REF_LINK ApplicationType), defining how the user interacts with it. For example: Batch,
  web and remote desktop (VNC).

${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    private const val batchApplicationUseCase = "batch"
    private const val virtualMachineUseCase = "virtualMachine"
    private const val webUseCase = "web"
    private const val vncUseCase = "vnc"
    private const val fileExtensionUseCase = "fileExtensions"
    private const val defaultValuesUseCase = "defaultValues"

    @OptIn(UCloudApiExampleValue::class)
    override fun documentation() {
        useCase(
            batchApplicationUseCase,
            "Simple batch application",
            flow = {
                val user = basicUser()

                comment("""
                    Applications contain quite a lot of information. The most important pieces of information are
                    summarized below:
                    
                    - This Job will run a `BATCH` application
                      - See `invocation.applicationType`
                      
                    - The application should launch the `acme/batch:1.0.0` container
                      - `invocation.tool.tool.description.backend`
                      - `invocation.tool.tool.description.image`
                      
                    - The command-line invocation will look like this: `acme-batch --debug "Hello, World!"`. 
                      - The invocation is created from `invocation.invocation`
                      - With parameters defined in `invocation.parameters`
                """.trimIndent())

                success(
                    findByNameAndVersion,
                    FindApplicationAndOptionalDependencies(
                        exampleBatchApplication.metadata.name,
                        exampleBatchApplication.metadata.version
                    ),
                    ApplicationWithFavoriteAndTags(
                        exampleBatchApplication.metadata,
                        exampleBatchApplication.invocation,
                        false,
                        emptyList()
                    ),
                    user
                )
            }
        )

        useCase(
            virtualMachineUseCase,
            "Simple virtual machine",
            flow = {
                val user = basicUser()

                comment("""
                    This example shows an Application encoding a virtual machine. It will use the
                    "acme-operating-system" as its base image, as defined in the Tool. 
                """.trimIndent())

                val application = exampleApplication(
                    "acme-os",
                    "1.0.0",
                    "acme-operating-system",
                    emptyList(),
                    emptyList(),
                    ToolBackend.VIRTUAL_MACHINE
                )

                success(
                    findByNameAndVersion,
                    FindApplicationAndOptionalDependencies(application.metadata.name, application.metadata.version),
                    ApplicationWithFavoriteAndTags(application.metadata, application.invocation, false, emptyList()),
                    user
                )
            }
        )

        useCase(
            webUseCase,
            "Simple web application",
            flow = {
                val user = basicUser()

                comment("""
                    This example shows an Application with a graphical web interface. The web server, hosting the 
                    interface, runs on port 8080 as defined in the `invocation.web` section.
                """.trimIndent())

                val application = exampleApplication(
                    "acme-web",
                    "1.0.0",
                    "acme/web:1.0.0",
                    listOf(
                        WordInvocationParameter("web-server")
                    ),
                    emptyList(),
                    type = ApplicationType.WEB
                ) { invocation ->
                    invocation.copy(web = WebDescription(8080))
                }

                success(
                    findByNameAndVersion,
                    FindApplicationAndOptionalDependencies(application.metadata.name, application.metadata.version),
                    ApplicationWithFavoriteAndTags(application.metadata, application.invocation, false, emptyList()),
                    user
                )
            }
        )

        useCase(
            vncUseCase,
            "Simple remote desktop application (VNC)",
            flow = {
                val user = basicUser()

                comment("""
                    This example shows an Application with a graphical web interface. The VNC server, hosting the 
                    interface, runs on port 5900 as defined in the `invocation.vnc` section.
                """.trimIndent())

                val application = exampleApplication(
                    "acme-remote-desktop",
                    "1.0.0",
                    "acme/remote-desktop:1.0.0",
                    listOf(
                        WordInvocationParameter("vnc-server")
                    ),
                    emptyList(),
                    type = ApplicationType.VNC
                ) { invocation ->
                    invocation.copy(vnc = VncDescription())
                }

                success(
                    findByNameAndVersion,
                    FindApplicationAndOptionalDependencies(application.metadata.name, application.metadata.version),
                    ApplicationWithFavoriteAndTags(application.metadata, application.invocation, false, emptyList()),
                    user
                )
            }
        )

        useCase(
            fileExtensionUseCase,
            "Registering a file handler",
            flow = {
                val user = basicUser()

                comment("""
                    This example shows an Application with a graphical web interface. The web server, hosting the 
                    interface, runs on port 8080 as defined in the `invocation.web` section.
                """.trimIndent())

                comment("""
                    The Application also registers a file handler of all files with the `*.c` extension. This is used as
                    a hint for the frontend that files with this extension can be opened with this Application. When
                    opened like this, the file's parent folder will be mounted as a resource.
                """.trimIndent())

                val application = exampleApplication(
                    "acme-web",
                    "1.0.0",
                    "acme/web:1.0.0",
                    listOf(
                        WordInvocationParameter("web-server")
                    ),
                    emptyList(),
                    type = ApplicationType.WEB
                ) { invocation ->
                    invocation.copy(
                        web = WebDescription(8080),
                        fileExtensions = listOf(".c")
                    )
                }

                success(
                    findByNameAndVersion,
                    FindApplicationAndOptionalDependencies(application.metadata.name, application.metadata.version),
                    ApplicationWithFavoriteAndTags(application.metadata, application.invocation, false, emptyList()),
                    user
                )
            }
        )

        useCase(
            defaultValuesUseCase,
            "An Application with default values",
            flow = {
                val user = basicUser()

                comment("""
                    This example shows an Application which has a single input parameter. The parameter contains a 
                    textual value. If the user does not provide a specific value, it will default to 'hello'. UCloud 
                    passes this value as the first argument on the command-line.
                """.trimIndent())

                val application = exampleApplication(
                    "acme-web",
                    "1.0.0",
                    "acme/web:1.0.0",
                    listOf(
                        WordInvocationParameter("web-server"),
                        VariableInvocationParameter(listOf("variable"))
                    ),
                    listOf(
                        ApplicationParameter.Text(
                            "variable",
                            optional = true,
                            defaultValue = defaultMapper.encodeToJsonElement<AppParameterValue>(
                                AppParameterValue.Text("hello")
                            ),
                            "My Variable",
                            description = "A variable passed to the Application (default = 'hello')"
                        )
                    ),
                    type = ApplicationType.WEB
                ) { invocation -> invocation.copy(web = WebDescription(8080)) }

                success(
                    findByNameAndVersion,
                    FindApplicationAndOptionalDependencies(application.metadata.name, application.metadata.version),
                    ApplicationWithFavoriteAndTags(application.metadata, application.invocation, false, emptyList()),
                    user
                )
            }
        )
    }

    val toggleFavorite = call("toggleFavorite", FavoriteRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"favorites"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Toggles the favorite status of an Application for the current user"
        }
    }

    val retrieveFavorites = call("retrieveFavorites", PaginationRequest.serializer(), Page.serializer(ApplicationSummaryWithFavorite.serializer()), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"favorites"
                }

                params {
                    +boundTo(PaginationRequest::itemsPerPage)
                    +boundTo(PaginationRequest::page)
                }
            }

            documentation {
                summary = "Retrieves the list of favorite Applications for the curent user"
            }
        }

    val searchTags = call("searchTags", TagSearchRequest.serializer(), Page.serializer(ApplicationSummaryWithFavorite.serializer()), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"searchTags"
            }

            params {
                +boundTo(TagSearchRequest::query)
                +boundTo(TagSearchRequest::excludeTools)
                +boundTo(TagSearchRequest::itemsPerPage)
                +boundTo(TagSearchRequest::page)
            }
        }

        documentation {
            summary = "Browses the Application catalog by tag"
        }
    }

    val searchApps = call("searchApps", AppSearchRequest.serializer(), Page.serializer(ApplicationSummaryWithFavorite.serializer()), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"search"
            }

            params {
                +boundTo(AppSearchRequest::query)
                +boundTo(AppSearchRequest::itemsPerPage)
                +boundTo(AppSearchRequest::page)
            }
        }

        documentation {
            summary = "Searches in the Application catalog using a free-text query"
        }
    }

    val findByName = call("findByName", FindByNameAndPagination.serializer(), Page.serializer(ApplicationSummaryWithFavorite.serializer()), CommonErrorMessage.serializer()) {
            auth {
                roles = Roles.AUTHENTICATED
                access = AccessRight.READ
            }

            http {
                path {
                    using(baseContext)
                    +"byName"
                }

                params {
                    +boundTo(FindByNameAndPagination::appName)
                    +boundTo(FindByNameAndPagination::itemsPerPage)
                    +boundTo(FindByNameAndPagination::page)
                }
            }

            documentation {
                summary = "Finds Applications given an exact name"
            }
        }

    val isPublic = call("isPublic", IsPublicRequest.serializer(), IsPublicResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                roles = Roles.PRIVILEGED
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"isPublic"
                }

                body {
                    bindEntireRequestFromBody()

                }
            }

            documentation {
                summary = "Checks if an Application is publicly accessible"
            }
        }

    val setPublic = call("setPublic", SetPublicRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = setOf(Role.ADMIN, Role.SERVICE, Role.PROVIDER)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"setPublic"
            }

            body {
                bindEntireRequestFromBody()
            }
        }

        documentation {
            summary = "Changes the 'publicly accessible' status of an Application"
        }
    }

    val advancedSearch = call("advancedSearch", AdvancedSearchRequest.serializer(), Page.serializer(ApplicationSummaryWithFavorite.serializer()),CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"advancedSearch"
            }

            body {
                bindEntireRequestFromBody()
            }
        }

        documentation {
            summary = "Searches in the Application catalog using more advanced parameters"
        }
    }

    val findByNameAndVersion = call("findByNameAndVersion", FindApplicationAndOptionalDependencies.serializer(), ApplicationWithFavoriteAndTags.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"byNameAndVersion"
            }

            params {
                +boundTo(FindApplicationAndOptionalDependencies::appName)
                +boundTo(FindApplicationAndOptionalDependencies::appVersion)
            }
        }

        documentation {
            summary = "Retrieves an Application by name and version"
        }
    }

    val hasPermission = call("hasPermission", HasPermissionRequest.serializer(), Boolean.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"permission"
            }

            params {
                +boundTo(HasPermissionRequest::appName)
                +boundTo(HasPermissionRequest::appVersion)
                +boundTo(HasPermissionRequest::permission)
            }
        }

        documentation {
            summary = "Check if an entity has permission to use a specific Application"
        }
    }

    val listAcl = call("listAcl", ListAclRequest.serializer(), ListSerializer(DetailedEntityWithPermission.serializer()), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"list-acl"
            }

            params {
                +boundTo(ListAclRequest::appName)
            }
        }

        documentation {
            summary = "Retrieves the permission information associated with an Application"
        }
    }

    val updateAcl = call("updateAcl", UpdateAclRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = setOf(Role.ADMIN, Role.SERVICE, Role.PROVIDER)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"updateAcl"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Updates the permissions associated with an Application"
        }
    }

    val findBySupportedFileExtension = call("findBySupportedFileExtension", FindBySupportedFileExtension.serializer(), PageV2.serializer(ApplicationWithExtension.serializer()), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    + "bySupportedFileExtension"
                }

                body {
                    bindEntireRequestFromBody()
                }
            }

            documentation {
                summary = "Finds a page of Application which can open a specific UFile"
            }
        }

    val findLatestByTool = call("findLatestByTool", FindLatestByToolRequest.serializer(), Page.serializer(Application.serializer()), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"byTool"
            }

            params {
                +boundTo(FindLatestByToolRequest::tool)
                +boundTo(FindLatestByToolRequest::itemsPerPage)
                +boundTo(FindLatestByToolRequest::page)
            }
        }

        documentation {
            summary = "Retrieves the latest version of an Application using a specific tool"
        }
    }

    val listAll = call("listAll", PaginationRequest.serializer(), Page.serializer(ApplicationSummaryWithFavorite.serializer()), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
            }

            params {
                +boundTo(PaginationRequest::itemsPerPage)
                +boundTo(PaginationRequest::page)
            }
        }

        documentation {
            summary = "Lists all Applications"
            description = "Results are not ordered in any specific fashion"
        }
    }

    val store = call("store", AppStoreSectionsRequest.serializer(), AppStoreSectionsResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"store"
            }

            params {
                +boundTo(AppStoreSectionsRequest::page)
            }

            documentation {
                summary = "Returns the application catalog sections"
            }
        }
    }

    val setGroup = call("setGroup", SetGroupRequest.serializer(), SetGroupResponse.serializer(), CommonErrorMessage.serializer())  {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"group/"
                +"set"
            }
            body { bindEntireRequestFromBody() }
        }
    }

    val createGroup = call("createGroup", CreateGroupRequest.serializer(), CreateGroupResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"group"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val deleteGroup = call("deleteGroup", DeleteGroupRequest.serializer(), DeleteGroupResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete
            path {
                using(baseContext)
                +"group"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val updateGroup = call("updateGroup", UpdateGroupRequest.serializer(), UpdateGroupResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"group/"
                +"update"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val listGroups = call("listGroups", ListGroupsRequest.serializer(), ListSerializer(ApplicationGroup.serializer()), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get
            path {
                using(baseContext)
                +"groups"
            }
        }
    }

    val retrieveGroup = call("retrieveGroup", RetrieveGroupRequest.serializer(), RetrieveGroupResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get
            path {
                using(baseContext)
                +"group"
            }
            params {
                +boundTo(RetrieveGroupRequest::id)
                +boundTo(RetrieveGroupRequest::name)
            }
        }
    }

    val create = call("create", Unit.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = setOf(Role.ADMIN, Role.SERVICE, Role.PROVIDER)
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Put
            path { using(baseContext) }
            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Creates a new Application and inserts it into the catalog"
        }
    }

    val updateLanding = call("updateLanding", Unit.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = setOf(Role.ADMIN, Role.SERVICE)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put
            path {
                using(baseContext)
                +"updateLanding"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Updates the landing page of the application store"
        }
    }

    val updateOverview = call("updateOverview", Unit.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = setOf(Role.ADMIN, Role.SERVICE)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put
            path {
                using(baseContext)
                +"updateOverview"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Updates the overview page of the application store"
        }
    }


    val delete = call("delete", DeleteAppRequest.serializer(), DeleteAppResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.ADMIN
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Removes an Application from the catalog"
        }
    }

    val createTag = call("createTag", CreateTagsRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"createTag"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Attaches a set of tags to an Application"
        }
    }

    val removeTag = call("removeTag", DeleteTagsRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"deleteTag"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Removes a set of tags from an Application"
        }
    }

    val listTags = call("listTags", Unit.serializer(), ListSerializer(String.serializer()), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"listTags"
            }
        }

        documentation {
            summary = "List all application tags"
        }
    }

    val uploadGroupLogo = call("uploadGroupLogo", UploadApplicationLogoRequest.serializer(), UploadApplicationLogoResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"group/"
                +"uploadLogo"
            }

            headers {
                +boundTo("Upload-Name", UploadApplicationLogoRequest::name)
            }
        }

        documentation {
            summary = "Uploads a logo and associates it with a group"
        }
    }

    val clearGroupLogo = call("clearGroupLogo", ClearLogoRequest.serializer(), ClearLogoResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"group/"
                +"clearLogo"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Removes a logo associated with a group"
        }
    }


    val fetchGroupLogo = call("fetchLogo", FetchLogoRequest.serializer(), FetchLogoResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"group/"
                +"logo"
            }

            params {
                +boundTo(FetchLogoRequest::name)
            }
        }

        documentation {
            summary = "Retrieves a logo associated with a group"
        }
    }

    val uploadLogo = call("uploadLogo", UploadApplicationLogoRequest.serializer(), UploadApplicationLogoResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                roles = Roles.PRIVILEGED
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"uploadLogo"
                }

                headers {
                    +boundTo("Upload-Name", UploadApplicationLogoRequest::name)
                }

                /*
                body {
                    bindToSubProperty(UploadApplicationLogoRequest::data)
                }
                 */
            }

            documentation {
                summary = "Uploads a logo and associates it with an Application"
            }
        }

    val clearLogo = call("clearLogo", ClearLogoRequest.serializer(), ClearLogoResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                roles = Roles.PRIVILEGED
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Delete

                path {
                    using(baseContext)
                    +"clearLogo"
                }

                body { bindEntireRequestFromBody() }
            }

            documentation {
                summary = "Removes a logo associated with an Application"
            }
        }


    val fetchLogo = call("fetchLogo", FetchLogoRequest.serializer(), FetchLogoResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"logo"
            }

            params {
                +boundTo(FetchLogoRequest::name)
            }
        }

        documentation {
            summary = "Retrieves a logo associated with an Application"
        }
    }

    @UCloudApiInternal(InternalLevel.BETA)
    val devImport = call("devImport", DevImportRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "devImport", roles = Roles.PRIVILEGED)

        documentation {
            summary = "An endpoint for importing applications - Only usable in dev environments"
        }
    }
}
