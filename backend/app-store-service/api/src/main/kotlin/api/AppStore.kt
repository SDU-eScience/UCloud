package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

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

$TYPE_REF Application s can be further divided into groups. An $TYPE_REF ApplicationGroup does not influence the
invocation of the application, but is used solely to visually group applications on UCloud's application store.

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

                comment(
                    """
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
                """.trimIndent()
                )

                success(
                    findByNameAndVersion,
                    FindByNameAndVersionRequest(
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

                comment(
                    """
                    This example shows an Application encoding a virtual machine. It will use the
                    "acme-operating-system" as its base image, as defined in the Tool. 
                """.trimIndent()
                )

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
                    FindByNameAndVersionRequest(application.metadata.name, application.metadata.version),
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

                comment(
                    """
                    This example shows an Application with a graphical web interface. The web server, hosting the 
                    interface, runs on port 8080 as defined in the `invocation.web` section.
                """.trimIndent()
                )

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
                    FindByNameAndVersionRequest(application.metadata.name, application.metadata.version),
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

                comment(
                    """
                    This example shows an Application with a graphical web interface. The VNC server, hosting the 
                    interface, runs on port 5900 as defined in the `invocation.vnc` section.
                """.trimIndent()
                )

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
                    FindByNameAndVersionRequest(application.metadata.name, application.metadata.version),
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

                comment(
                    """
                    This example shows an Application with a graphical web interface. The web server, hosting the 
                    interface, runs on port 8080 as defined in the `invocation.web` section.
                """.trimIndent()
                )

                comment(
                    """
                    The Application also registers a file handler of all files with the `*.c` extension. This is used as
                    a hint for the frontend that files with this extension can be opened with this Application. When
                    opened like this, the file's parent folder will be mounted as a resource.
                """.trimIndent()
                )

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
                    FindByNameAndVersionRequest(application.metadata.name, application.metadata.version),
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

                comment(
                    """
                    This example shows an Application which has a single input parameter. The parameter contains a 
                    textual value. If the user does not provide a specific value, it will default to 'hello'. UCloud 
                    passes this value as the first argument on the command-line.
                """.trimIndent()
                )

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
                    FindByNameAndVersionRequest(application.metadata.name, application.metadata.version),
                    ApplicationWithFavoriteAndTags(application.metadata, application.invocation, false, emptyList()),
                    user
                )
            }
        )
    }

    // Core CRUD
    // =================================================================================================================
    val findByName = LegacyApi.findByName
    val findByNameAndVersion = LegacyApi.findByNameAndVersion
    val create = LegacyApi.create
    val search = Search.call
    val browseOpenWithRecommendations = BrowseOpenWithRecommendations.call

    // Application management
    // =================================================================================================================
    val updateApplicationFlavor = UpdateApplicationFlavor.call
    val retrieveAcl = RetrieveAcl.call
    val updateAcl = UpdateAcl.call
    val updatePublicFlag = UpdatePublicFlag.call
    val listAllApplications = ListAllApplications.call

    // Starred applications
    // =================================================================================================================
    val toggleStar = ToggleStar.call
    val retrieveStars = RetrieveStars.call

    // Group management
    // =================================================================================================================
    val createGroup = CreateGroup.call
    val retrieveGroup = RetrieveGroup.call
    val browseGroups = BrowseGroups.call
    val updateGroup = UpdateGroup.call
    val deleteGroup = DeleteGroup.call
    val addLogoToGroup = AddLogoToGroup.call
    val removeLogoFromGroup = RemoveLogoFromGroup.call
    val retrieveGroupLogo = RetrieveGroupLogo.call
    val assignApplicationToGroup = AssignApplicationToGroup.call
    val retrieveAppLogo = RetrieveAppLogo.call

    // Category management
    // =================================================================================================================
    val createCategory = CreateCategory.call
    val browseCategories = BrowseCategories.call
    val retrieveCategory = RetrieveCategory.call
    val addGroupToCategory = AddGroupToCategory.call
    val removeGroupFromCategory = RemoveGroupFromCategory.call
    val assignPriorityToCategory = AssignPriorityToCategory.call
    val deleteCategory = DeleteCategory.call

    // Landing page management
    // =================================================================================================================
    val retrieveLandingPage = RetrieveLandingPage.call
    val retrieveCarrouselImage = RetrieveCarrouselImage.call

    // Spotlight management
    // =================================================================================================================
    val createSpotlight = CreateSpotlight.call
    val updateSpotlight = UpdateSpotlight.call
    val deleteSpotlight = DeleteSpotlight.call
    val retrieveSpotlight = RetrieveSpotlight.call
    val browseSpotlights = BrowseSpotlight.call
    val activateSpotlight = ActivateSpotlight.call

    // Carrousel management
    // =================================================================================================================
    val updateCarrousel = UpdateCarrousel.call
    val updateCarrouselImage = UpdateCarrouselImage.call

    // Top picks management
    // =================================================================================================================
    val updateTopPicks = UpdateTopPicks.call

    // Import API
    // =================================================================================================================
    val devImport = DevImport.call
    val importFromFile = ImportFromFile.call
    val export = Export.call

    // NOTE(Dan): Legacy API - do not touch
    object LegacyApi {
        // NOTE(Dan): Legacy API - do not touch
        val findByName = call(
            "findByName",
            FindByNameRequest.serializer(),
            Page.serializer(ApplicationSummaryWithFavorite.serializer()),
            CommonErrorMessage.serializer()
        ) {
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
                    +boundTo(FindByNameRequest::appName)
                    +boundTo(FindByNameRequest::itemsPerPage)
                    +boundTo(FindByNameRequest::page)
                }
            }

            documentation {
                summary = "Finds Applications given an exact name"
            }
        }

        // NOTE(Dan): Legacy API - do not touch
        val findByNameAndVersion = call(
            "findByNameAndVersion",
            FindByNameAndVersionRequest.serializer(),
            ApplicationWithFavoriteAndTags.serializer(),
            CommonErrorMessage.serializer()
        ) {
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
                    +boundTo(FindByNameAndVersionRequest::appName)
                    +boundTo(FindByNameAndVersionRequest::appVersion)
                }
            }

            documentation {
                summary =
                    "Retrieves an Application by name and version, or newest Application if version is not specified"
            }
        }

        // NOTE(Dan): Legacy API - do not touch
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
    }

    object ToggleStar {
        @Serializable
        data class Request(
            val name: String,
        )

        val call = call(
            "toggleStar",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "toggleStar")

                documentation {
                    summary = "Toggles the favorite status of an Application for the current user"
                }
            }
        )
    }

    object RetrieveStars {
        @Serializable
        data class Response(
            val items: List<ApplicationSummaryWithFavorite>,
        )

        val call = call(
            "retrieveStars",
            Unit.serializer(),
            Response.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "stars")

                documentation {
                    summary = "Retrieves the list of favorite Applications for the current user"
                }
            }
        )
    }

    object Search {
        @Serializable
        data class Request(
            val query: String,
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ) : WithPaginationRequestV2

        val call = call(
            "search",
            Request.serializer(),
            PageV2.serializer(ApplicationSummaryWithFavorite.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpSearch(baseContext)

                documentation {
                    summary = "Searches in the Application catalog using a free-text query"
                }
            }
        )
    }

    object UpdatePublicFlag {
        @Serializable
        data class Request(
            val name: String,
            val version: String,
            val public: Boolean,
        )

        val call = call(
            "updatePublicFlag",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updatePublicFlag", roles = Roles.PRIVILEGED)

                documentation {
                    summary = "Changes the 'publicly accessible' status of an Application"
                }
            }
        )
    }

    object RetrieveAcl {
        @Serializable
        data class Request(
            val name: String,
        )

        @Serializable
        data class Response(
            val entries: List<DetailedEntityWithPermission>,
        )

        val call = call(
            "retrieveAcl",
            Request.serializer(),
            Response.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "acl", roles = Roles.PRIVILEGED)

                documentation {
                    summary = "Retrieves the permission information associated with an Application"
                }
            }
        )
    }

    object UpdateAcl {
        @Serializable
        data class Request(
            val name: String,
            val changes: List<ACLEntryRequest>,
        )

        val call = call(
            "updateAcl",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateAcl", roles = Roles.PRIVILEGED)

                documentation {
                    summary = "Updates the permissions associated with an Application"
                }
            }
        )
    }

    object BrowseOpenWithRecommendations {
        @Serializable
        data class Request(
            val files: List<String>,
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ) : WithPaginationRequestV2

        val call = call(
            "browseOpenWithRecommendations",
            Request.serializer(),
            PageV2.serializer(ApplicationWithExtension.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "openWith")

                documentation {
                    summary = "Finds a page of Application which can open a specific UFile"
                }
            }
        )
    }

    object AssignApplicationToGroup {
        @Serializable
        data class Request(
            val name: String,
            val group: Int? = null,
        )

        val call = call(
            "assignApplicationToGroup",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "assignApplicationToGroup", roles = Roles.PRIVILEGED)
            }
        )
    }

    object CreateGroup {
        val call = call(
            "createGroup",
            ApplicationGroup.Specification.serializer(),
            FindByIntId.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "createGroup", roles = Roles.PRIVILEGED)
            }
        )
    }

    object DeleteGroup {
        val call = call(
            "deleteGroup",
            FindByIntId.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "deleteGroup", roles = Roles.PRIVILEGED)
            }
        )
    }

    object UpdateGroup {
        @Serializable
        data class Request(
            val id: Int,
            val newTitle: String? = null,
            val newDefaultFlavor: String? = null,
            val newDescription: String? = null,
            val newLogoHasText: Boolean? = null,
        )

        val call = call(
            "updateGroup",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateGroup", roles = Roles.PRIVILEGED)
            }
        )
    }

    object BrowseGroups {
        @Serializable
        data class Request(
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ) : WithPaginationRequestV2

        val call = call(
            "browseGroups",
            Request.serializer(),
            PageV2.serializer(ApplicationGroup.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpBrowse(baseContext, "groups", roles = Roles.PRIVILEGED)
            }
        )
    }

    object RetrieveGroup {
        val call = call(
            "retrieveGroup",
            FindByIntId.serializer(),
            ApplicationGroup.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "groups")
            }
        )
    }

    object UpdateApplicationFlavor {
        @Serializable
        data class Request(
            val applicationName: String,
            val flavorName: String?,
        )

        val call = call(
            "updateApplicationFlavor",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateApplicationFlavor", roles = Roles.PRIVILEGED)

                documentation {
                    summary = "Updates the flavor name for a set of applications"
                }
            }
        )
    }

    object CreateCategory {
        val call = call(
            "createCategory",
            ApplicationCategory.Specification.serializer(),
            FindByIntId.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "createCategory", roles = Roles.PRIVILEGED)
            }
        )
    }

    object AddGroupToCategory {
        @Serializable
        data class Request(
            val groupId: Int,
            val categoryId: Int,
        )

        val call = call(
            "addGroupToCategory",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "addGroupToCategory", roles = Roles.PRIVILEGED)
            }
        )
    }

    object RemoveGroupFromCategory {
        @Serializable
        data class Request(
            val groupId: Int,
            val categoryId: Int,
        )

        val call = call(
            "removeGroupFromCategory",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "removeGroupFromCategory", roles = Roles.PRIVILEGED)
            }
        )
    }

    object AssignPriorityToCategory {
        @Serializable
        data class Request(
            val id: Int,
            val priority: Int
        )

        val call = call(
            "assignPriorityToCategory",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "assignPriorityToCategory", roles = Roles.PRIVILEGED)
            }
        )
    }

    object BrowseCategories {
        @Serializable
        class Request(
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ) : WithPaginationRequestV2

        val call = call(
            "browseCategories",
            Request.serializer(),
            PageV2.serializer(ApplicationCategory.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpBrowse(baseContext, "categories", roles = Roles.END_USER)
            }
        )
    }

    object RetrieveCategory {
        val call = call(
            "retrieveCategory",
            FindByIntId.serializer(),
            ApplicationCategory.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "category")
            }
        )
    }

    object DeleteCategory {
        val call = call(
            "deleteCategory",
            FindByIntId.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "deleteCategory", roles = Roles.PRIVILEGED)
            }
        )
    }

    object AddLogoToGroup {
        @Serializable
        data class Request(
            val groupId: Int,
        )

        val call = call(
            "addLogoToGroup",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
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
                        +boundTo("Upload-Name", Request::groupId)
                    }
                }

                documentation {
                    summary = "Uploads a logo and associates it with a group"
                }
            }
        )
    }

    object RemoveLogoFromGroup {
        val call = call(
            "removeLogoFromGroup",
            FindByIntId.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "removeLogoFromGroup", roles = Roles.PRIVILEGED)
            }
        )
    }

    object RetrieveGroupLogo {
        @Serializable
        data class Request(
            val id: Int,
            val darkMode: Boolean = false,
            val includeText: Boolean = false,
            val placeTextUnderLogo: Boolean = false,
        )

        val call = call(
            "retrieveGroupLogo",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "groupLogo", roles = Roles.PUBLIC)
            }
        )
    }

    object RetrieveAppLogo {
        @Serializable
        data class Request(
            val name: String,
            val darkMode: Boolean = false,
            val includeText: Boolean = false,
            val placeTextUnderLogo: Boolean = false,
        )

        val call = call(
            "retrieveAppLogo",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "appLogo", roles = Roles.PUBLIC)
            }
        )
    }

    object DevImport {
        @Serializable
        data class Request(
            val endpoint: String,
            val checksum: String
        )

        @UCloudApiInternal(InternalLevel.BETA)
        val call = call(
            "devImport",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "devImport", roles = Roles.PRIVILEGED)
            }
        )
    }

    object ImportFromFile {
        @UCloudApiInternal(InternalLevel.BETA)
        val call = call(
            "importFromFile",
            Unit.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "importFromFile", roles = Roles.PRIVILEGED)
            }
        )
    }

    object Export {
        @UCloudApiInternal(InternalLevel.BETA)
        val call = call(
            "export",
            Unit.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "export", roles = Roles.PRIVILEGED)
            }
        )
    }

    object RetrieveLandingPage {
        @Serializable
        data class Response(
            val carrousel: List<CarrouselItem>,
            val topPicks: List<TopPick>,
            val categories: List<ApplicationCategory>,
            val spotlight: Spotlight?,
            val newApplications: List<ApplicationSummaryWithFavorite>,
            val recentlyUpdated: List<ApplicationSummaryWithFavorite>,
        )

        val call = call(
            "retrieveLandingPage",
            Unit.serializer(),
            Response.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "landingPage")
            }
        )
    }

    object RetrieveCarrouselImage {
        @Serializable
        data class Request(
            val index: Int,
            val slideTitle: String,
        )

        val call = call(
            "retrieveCarrouselImage",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "carrouselImage", roles = Roles.PUBLIC)
            }
        )
    }

    object ListAllApplications {
        @Serializable
        data class Response(
            val items: List<NameAndVersion>,
        )

        val call = call(
            "listAllApplications",
            Unit.serializer(),
            Response.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "allApplications", roles = Roles.PRIVILEGED)
            }
        )
    }


    object CreateSpotlight {
        val call = call(
            "createSpotlight",
            Spotlight.serializer(),
            FindByIntId.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "createSpotlight", roles = Roles.PRIVILEGED)
            }
        )
    }

    object UpdateSpotlight {
        val call = call(
            "updateSpotlight",
            Spotlight.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateSpotlight", roles = Roles.PRIVILEGED)
            }
        )
    }

    object DeleteSpotlight {
        val call = call(
            "deleteSpotlight",
            FindByIntId.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "deleteSpotlight", roles = Roles.PRIVILEGED)
            }
        )
    }

    object RetrieveSpotlight {
        val call = call(
            "retrieveSpotlight",
            FindByIntId.serializer(),
            Spotlight.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "spotlight", roles = Roles.PRIVILEGED)
            }
        )
    }

    object BrowseSpotlight {
        @Serializable
        data class Request(
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ): WithPaginationRequestV2

        val call = call(
            "browseSpotlight",
            Request.serializer(),
            PageV2.serializer(Spotlight.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpBrowse(baseContext, "spotlight", roles = Roles.PRIVILEGED)
            }
        )
    }

    object ActivateSpotlight {
        val call = call(
            "activateSpotlight",
            FindByIntId.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "activateSpotlight", roles = Roles.PRIVILEGED)
            }
        )
    }

    object UpdateCarrousel {
        @Serializable
        data class Request(
            val newSlides: List<CarrouselItem>,
        )

        val call = call(
            "updateCarrousel",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateCarrousel", roles = Roles.PRIVILEGED)
            }
        )
    }

    object UpdateCarrouselImage {
        @Serializable
        data class Request(
            val slideIndex: Int,
        )

        val call = call(
            "updateCarrouselImage",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                auth {
                    roles = Roles.PRIVILEGED
                    access = AccessRight.READ_WRITE
                }

                http {
                    method = HttpMethod.Post

                    path {
                        using(baseContext)
                        +"updateCarrouselImage"
                    }

                    headers {
                        +boundTo("Slide-Index", Request::slideIndex)
                    }
                }
            }
        )
    }

    object UpdateTopPicks {
        @Serializable
        data class Request(
            val newTopPicks: List<TopPick>,
        )

        val call = call(
            "updateTopPicks",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateTopPicks", roles = Roles.PRIVILEGED)
            }
        )
    }
}

@Serializable
data class TopPick(
    val title: String,
    val applicationName: String? = null,
    val groupId: Int? = null,
    val description: String,
    val defaultApplicationToRun: String? = null,
    val logoHasText: Boolean = false,
) {
    init {
        if (applicationName != null && groupId != null) {
            throw RPCException(
                "applicationName and groupId cannot be supplied at the same time!",
                HttpStatusCode.BadRequest
            )
        }

        if (applicationName == null && groupId == null) {
            throw RPCException(
                "Either applicationName or groupId must be supplied!",
                HttpStatusCode.BadRequest
            )
        }
    }
}

@Serializable
data class CarrouselItem(
    val title: String,
    val body: String,
    val imageCredit: String,
    val linkedApplication: String? = null,
    val linkedWebPage: String? = null,
    val linkedGroup: Int? = null,

    // if linkedGroup != null this will point to the default app. if linkedApplication != null then it will be equal
    // to linkedApplication
    val resolvedLinkedApp: String? = null,
) {
    init {
        checkSingleLine(::title, title)
        checkSingleLine(::imageCredit, imageCredit)
        checkTextLength(::body, body, maximumSize = 400)

        var linkedItems = 0
        if (linkedApplication != null) linkedItems++
        if (linkedGroup != null) linkedItems++
        if (linkedWebPage != null) linkedItems++

        if (linkedItems != 1) {
            throw RPCException(
                "Exactly one of linkedApplication, linkedWebPage or linkedGroup must be supplied!",
                HttpStatusCode.BadRequest
            )
        }

        if (linkedApplication != null) checkSingleLine(::linkedApplication, linkedApplication)
        if (linkedWebPage != null) checkSingleLine(::linkedWebPage, linkedWebPage)
    }
}

@Serializable
data class Spotlight(
    val title: String,
    val body: String,
    val applications: List<TopPick>,
    val active: Boolean,
    val id: Int? = null,
)

@Serializable
data class ApplicationGroup(
    val metadata: Metadata,
    val specification: Specification,
    val status: Status = Status(),
) {
    @Serializable
    data class Metadata(
        val id: Int,
    )

    @Serializable
    data class Specification(
        val title: String,
        val description: String,
        val defaultFlavor: String? = null,
        val categories: Set<Int> = emptySet(),
        val colorReplacement: ColorReplacements = ColorReplacements(),
        val logoHasText: Boolean = false,
    )

    @Serializable
    data class ColorReplacements(val light: Map<Int, Int>? = null, val dark: Map<Int, Int>? = null)

    @Serializable
    data class Status(
        val applications: List<ApplicationSummaryWithFavorite>? = null,
    )
}

@Serializable
data class ApplicationCategory(
    val metadata: Metadata,
    val specification: Specification,
    val status: Status = Status(),
) {
    @Serializable
    data class Metadata(
        val id: Int,
    )

    @Serializable
    data class Specification(
        val title: String,
        val description: String? = null,
    )

    @Serializable
    data class Status(
        val groups: List<ApplicationGroup>? = null,
    )
}

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
@UCloudApiDoc("Request type to find a Page of resources defined by a name")
data class FindByNameRequest(
    val appName: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest

@Serializable
@UCloudApiDoc("A request type to find a resource by name and version")
data class FindByNameAndVersion(val name: String, val version: String)

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
data class FindByNameAndVersionRequest(
    val appName: String,
    val appVersion: String? = null
)

@Serializable
data class ACLEntryRequest(
    val entity: AccessEntity,
    val rights: ApplicationAccessRight,
    val revoke: Boolean = false
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
                ApplicationGroup.Metadata(0),
                ApplicationGroup.Specification("Test Group", "", null, emptySet())
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
