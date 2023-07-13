package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.Roles
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.Maintenance
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.AuthProvidersRefreshRequestItem
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.HostInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiStable
@UCloudApiDoc("""
    Providers, the backbone of UCloud, expose compute and storage resources to end-users.
    
    You can read more about providers [here](/docs/developer-guide/accounting-and-projects/providers.md).
""")
data class Provider(
    override val id: String,
    override val specification: ProviderSpecification,
    val refreshToken: String,
    val publicKey: String,
    override val createdAt: Long,
    override val status: ProviderStatus,
    override val updates: List<ProviderUpdate>,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions? = null
) : Resource<Product, ProviderSupport> {
    override fun toString(): String {
        return "Provider(id='$id', specification=$specification, createdAt=$createdAt, status=$status, " +
                "owner=$owner)"
    }

    override fun visualize(): DocVisualization {
        val baseVisualization = super.visualize() as DocVisualization.Card
        return baseVisualization.copy(lines = baseVisualization.lines + listOf(
            DocStatLine.of("refreshToken" to visualizeValue(refreshToken)),
            DocStatLine.of("publicKey" to visualizeValue(publicKey)),
        ))
    }

    companion object {
        const val UCLOUD_CORE_PROVIDER = "ucloud_core"
    }
}

@Serializable
@UCloudApiDoc("The specification of a Provider contains basic (network) contact information")
@UCloudApiStable
data class ProviderSpecification(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int? = null,
) : ResourceSpecification, DocVisualizable {
    override val product: ProductReference = ProductReference("", "", Provider.UCLOUD_CORE_PROVIDER)

    fun toHostInfo(): HostInfo =
        HostInfo(domain, if (https) "https" else "http", port)

    override fun visualize(): DocVisualization = DocVisualization.Inline(buildString {
        if (https) {
            append("https://")
        } else {
            append("http://")
        }
        append(domain)
        if (port != null) {
            append(":")
            append(port)
        }
    })
}

fun ProviderSpecification.addProviderInfoToRelativeUrl(url: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    return buildString {
        if (https) {
            append("https://")
        } else {
            append("http://")
        }

        append(domain)

        if (port != null) {
            append(":")
            append(port)
        }

        append('/')
        append(url.removePrefix("/"))
    }
}

@Serializable
@UCloudApiDoc("A placeholder document used only to conform with the Resources API")
@UCloudApiStable
data class ProviderSupport(
    override val product: ProductReference,
    override var maintenance: Maintenance? = null,
) : ProductSupport

@Serializable
@UCloudApiDoc("A placeholder document used only to conform with the Resources API")
@UCloudApiStable
data class ProviderStatus(
    @UCloudApiDoc("📝 NOTE: Always null")
    override var resolvedSupport: ResolvedSupport<Product, ProviderSupport>? = null,
    @UCloudApiDoc("📝 NOTE: Always null")
    override var resolvedProduct: Product? = null
) : ResourceStatus<Product, ProviderSupport>

@Serializable
@UCloudApiOwnedBy(Providers::class)
@UCloudApiDoc("Updates regarding a Provider, not currently in use")
@UCloudApiStable
data class ProviderUpdate(
    override val timestamp: Long,
    override val status: String? = null,
) : ResourceUpdate

typealias ProvidersUpdateSpecificationRequest = BulkRequest<ProviderSpecification>
typealias ProvidersUpdateSpecificationResponse = BulkResponse<FindByStringId>

@Serializable
@UCloudApiDoc("Request type for renewing the tokens of a Provider")
@UCloudApiStable
data class ProvidersRenewRefreshTokenRequestItem(val id: String)
typealias ProvidersRenewRefreshTokenResponse = Unit

typealias ProvidersRetrieveSpecificationRequest = FindByStringId
typealias ProvidersRetrieveSpecificationResponse = ProviderSpecification

@Serializable
@UCloudApiDoc("Flags used to tweak read queries")
@UCloudApiStable
data class ProviderIncludeFlags(
    override val includeOthers: Boolean = false,
    override val includeUpdates: Boolean = false,
    override val includeSupport: Boolean = false,
    override val includeProduct: Boolean = false,
    override val filterCreatedBy: String? = null,
    override val filterCreatedAfter: Long? = null,
    override val filterCreatedBefore: Long? = null,
    override val filterProvider: String? = null,
    override val filterProductId: String? = null,
    override val filterProductCategory: String? = null,
    override val filterProviderIds: String? = null,
    override val filterIds: String? = null,
    val filterName: String? = null,
    override val hideProductId: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProvider: String? = null,
) : ResourceIncludeFlags

@Serializable
@UCloudApiDoc("Request type used as part of the approval process")
@Deprecated("Use the simpler register endpoint instead")
@UCloudApiStable
sealed class ProvidersRequestApprovalRequest {
    @Serializable
    @SerialName("information")
    @UCloudApiDoc("Request type used as part of the approval process, provides contact information")
    @Deprecated("Use the simpler register endpoint instead")
    @UCloudApiStable
    data class Information(val specification: ProviderSpecification) : ProvidersRequestApprovalRequest()

    @Serializable
    @SerialName("sign")
    @Deprecated("Use the simpler register endpoint instead")
    @UCloudApiDoc("Request type used as part of the approval process, associates a UCloud user to previously uploaded " +
        "information")
    @UCloudApiStable
    data class Sign(val token: String) : ProvidersRequestApprovalRequest()
}

@Serializable
@UCloudApiDoc("Response type used as part of the approval process")
@Deprecated("Use the simpler register endpoint instead")
@UCloudApiStable
sealed class ProvidersRequestApprovalResponse {
    @Serializable
    @SerialName("requires_signature")
    @UCloudApiDoc("Response type used as part of the approval process")
    @Deprecated("Use the simpler register endpoint instead")
    @UCloudApiStable
    data class RequiresSignature(val token: String) : ProvidersRequestApprovalResponse()

    @Serializable
    @SerialName("awaiting_admin_approval")
    @UCloudApiDoc("Response type used as part of the approval process")
    @Deprecated("Use the simpler register endpoint instead")
    @UCloudApiStable
    data class AwaitingAdministratorApproval(val token: String) : ProvidersRequestApprovalResponse()
}

@Serializable
@UCloudApiDoc("Request type used as part of the approval process")
@Deprecated("Use the simpler register endpoint instead")
@UCloudApiStable
data class ProvidersApproveRequest(val token: String)
typealias ProvidersApproveResponse = FindByStringId

@UCloudApiStable
object Providers : ResourceApi<Provider, ProviderSpecification, ProviderUpdate, ProviderIncludeFlags, ProviderStatus,
        Product, ProviderSupport>("providers") {
    init {
        serializerLookupTable = mapOf(
            serializerEntry(ProvidersRequestApprovalRequest.serializer()),
            serializerEntry(ProvidersRequestApprovalResponse.serializer()),
            serializerEntry(Product.serializer()),
            serializerEntry(Product.Compute.serializer()),
            serializerEntry(Product.Ingress.serializer()),
            serializerEntry(Product.NetworkIP.serializer()),
            serializerEntry(Product.Storage.serializer()),
            serializerEntry(Product.License.serializer()),
        )

        description = """
            Providers, the backbone of UCloud, expose compute and storage resources to end-users.

            UCloud/Core is an orchestrator of $TYPE_REF Resource s. This means, that the core doesn't actually know how 
            to serve files or run computational workloads. Instead, the core must ask one or more $TYPE_REF Provider s 
            to fulfil requests from the user.

            ![](/backend/accounting-service/wiki/overview.png)

            __Figure:__ UCloud/Core receives a request from the user and forwards it to a provider.

            The core isn't a simple proxy. Before passing the request, UCloud performs the following tasks:

            - __Authentication:__ UCloud ensures that users have authenticated.
            - __Authorization:__ The $TYPE_REF dk.sdu.cloud.project.api.v2.Project system of UCloud brings role-based 
              authorization to all $TYPE_REF Resource s. The core verifies all actions before forwarding the request.
            - __Resolving references:__ UCloud maintains a catalog of all $TYPE_REF Resource s in the system. All user 
              requests only contain a reference to these $TYPE_REF Resource s. UCloud verifies and resolves all 
              references before proxying the request.

            The communication between UCloud/Core and the provider happens through the __provider APIs__. Throughout the 
            developer guide, you will find various sections describing these APIs. These APIs contain both an ingoing 
            (from the provider's perspective) and outgoing APIs. This allows for bidirectional communication between 
            both parties. In almost all cases, the communication from the user goes through UCloud/Core. The only 
            exception to this rule is when the data involved is either sensitive or large. In these cases, UCloud will 
            only be responsible for facilitating direct communication. A common example of this is 
            [file uploads]($CALL_REF_LINK files.createUpload).
            
            ## Suggested Reading
            
            - [Products](/docs/developer-guide/accounting-and-projects/products.md)
            - [Authenticating as a Provider](/docs/developer-guide/core/users/authentication/providers.md)
            - [API Conventions and UCloud RPC](/docs/developer-guide/core/api-conventions.md)
        """.trimIndent()
    }

    private const val exampleProviderUseCase = "provider"
    private const val registrationUseCase = "registration"
    private const val authenticationUseCase = "authentication"

    override fun documentation() {
        useCase(
            exampleProviderUseCase,
            "Definition of a Provider (Retrieval)",
            flow = {
                val admin = administrator()

                comment("""
                    This example shows an example provider. The provider's specification contains basic contact
                    information. This information is used by UCloud when it needs to communicate with a provider.
                """.trimIndent())

                success(
                    retrieveSpecification,
                    FindByStringId("51231"),
                    ProvidersRetrieveSpecificationResponse(
                        "example",
                        "provider.example.com",
                        true,
                        port = 443
                    ),
                    admin
                )
            }
        )

        useCase(
            registrationUseCase,
            "Registering a Provider",
            flow = {
                val integrationModule = actor("integrationModule", "The integration module (unauthenticated)")
                val systemAdministrator = actor(
                    "systemAdministrator",
                    "The admin of the provider (authenticated as a normal UCloud user)"
                )
                val admin = administrator()

                val specification = ProviderSpecification(
                    "example",
                    "provider.example.com",
                    true
                )

                comment(
                    """
                        WARNING: The following flow still works, but is no longer used by the default configuration of
                        the integration module. Instead, it has been replaced by the much simpler approach of having
                        a UCloud administrator register the provider manually and then exchange tokens out-of-band.
                    """.trimIndent()
                )

                comment("""
                    This example shows how a Provider registers with UCloud/Core. In this example, the Provider will 
                    be using the Integration Module. The system administrator, of the Provider, has just installed the 
                    Integration Module. Before starting the module, the system administrator has configured the module 
                    to contact UCloud at a known address.
                """.trimIndent())

                comment("""
                    When the system administrator launches the Integration Module, it will automatically contact 
                    UCloud. This request contains the contact information back to the Provider.
                """.trimIndent())

                success(
                    requestApproval,
                    ProvidersRequestApprovalRequest.Information(specification),
                    ProvidersRequestApprovalResponse.RequiresSignature("9eb96d0a27b1330cdc727ef4316bd48265f71414"),
                    integrationModule
                )

                comment("""
                    UCloud/Core responds with a token and the IM displays a link to the sysadmin. The sysadmin follows 
                    this link, and authenticates with their own UCloud user. This triggers the following request:
                """.trimIndent())

                success(
                    requestApproval,
                    ProvidersRequestApprovalRequest.Sign(
                        "9eb96d0a27b1330cdc727ef4316bd48265f71414"
                    ),
                    ProvidersRequestApprovalResponse.RequiresSignature("9eb96d0a27b1330cdc727ef4316bd48265f71414"),
                    integrationModule
                )

                comment("""
                    The sysadmin now sends his token to a UCloud administrator. This communication always happen 
                    out-of-band. For a production system, we expect to have been in a dialogue with you about this 
                    process already.

                    The UCloud administrator approves the request.
                """.trimIndent())

                success(
                    approve,
                    ProvidersApproveRequest("9eb96d0a27b1330cdc727ef4316bd48265f71414"),
                    ProvidersApproveResponse("51231"),
                    admin
                )

                comment("""
                    UCloud/Core sends a welcome message to the Integration Module. The core uses the original token to 
                    authenticate the request. The request also contains the refreshToken and publicKey required by the 
                    IM. Under normal circumstances, the IM will auto-configure itself to use these tokens.
                """.trimIndent())

                val response = Provider(
                    "51231",
                    specification,
                    "8accc446c2e3ac924ff07c77d93e1679378a5dad",
                    "~~ public key ~~",
                    1633329776235,
                    ProviderStatus(),
                    emptyList(),
                    ResourceOwner("sysadmin", null),
                )

                success(
                    IntegrationProvider("example").welcome,
                    IntegrationProviderWelcomeRequest(
                        "9eb96d0a27b1330cdc727ef4316bd48265f71414",
                        ProviderWelcomeTokens(response.refreshToken, response.publicKey)
                    ),
                    Unit,
                    ucloudCore()
                )

                comment("""
                    Alternatively, the sysadmin can read the tokens and perform manual configuration.
                """.trimIndent())

                success(
                    retrieve,
                    ResourceRetrieveRequest(ProviderIncludeFlags(), "51231"),
                    response,
                    systemAdministrator
                )
            }
        )
        useCase(
            authenticationUseCase,
            "A Provider authenticating with UCloud/Core",
            preConditions = listOf(
                "The provider has already been registered with UCloud/Core",
            ),
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                comment("📝 Note: The tokens shown here are not representative of tokens you will see in practice")

                success(
                    AuthProviders.refresh,
                    bulkRequestOf(
                        AuthProvidersRefreshRequestItem("fb69e4367ee0fe4c76a4a926394aee547a41d998")
                    ),
                    BulkResponse(
                        listOf(
                            AccessToken(
                                "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9." +
                                        "eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9." +
                                        "P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo"
                            )
                        )
                    ),
                    provider
                )
            }
        )

        document(browse, UCloudApiDocC("""
            Browses the catalog of available Providers
            
            This endpoint can only be used my users who either own a Provider or are a UCloud administrator.
        """.trimIndent())
        )

        document(retrieve, UCloudApiDocC("""
            Retrieves a single Provider
            
            This endpoint can only be used my users who either own a Provider or are a UCloud administrator.
        """.trimIndent()))

        document(
            retrieveProducts,
            UCloudApiDocC("Not in use, only here to be compliant with Resources API", importance = -1000)
        )

        document(
            create,
            UCloudApiDocC("""
                Creates one or more Providers
                
                This endpoint can only be invoked by a UCloud administrator.
            """.trimIndent())
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        Provider.serializer(),
        typeOfIfPossible<Provider>(),
        ProviderSpecification.serializer(),
        typeOfIfPossible<ProviderSpecification>(),
        ProviderUpdate.serializer(),
        typeOfIfPossible<ProviderUpdate>(),
        ProviderIncludeFlags.serializer(),
        typeOfIfPossible<ProviderIncludeFlags>(),
        ProviderStatus.serializer(),
        typeOfIfPossible<ProviderStatus>(),
        ProviderSupport.serializer(),
        typeOfIfPossible<ProviderSupport>(),
        Product.serializer(),
        typeOfIfPossible<Product>()
    )

    override val create get() = super.create!!
    override val delete: Nothing? = null
    override val search get() = super.search!!

    val update = call("update", BulkRequest.serializer(ProviderSpecification.serializer()), BulkResponse.serializer(FindByStringId.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "update", Roles.END_USER)

        documentation {
            summary = "Updates the specification of one or more providers"
            description = """
                This endpoint can only be invoked by a UCloud administrator.
            """
        }
    }

    val renewToken = call("renewToken", BulkRequest.serializer(ProvidersRenewRefreshTokenRequestItem.serializer()), ProvidersRenewRefreshTokenResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "renewToken")

        documentation {
            summary = "Replaces the current refresh-token and certificate of a Provider"
            description = """
                ---
                
                __⚠️ WARNING:__ This endpoint will _immediately_ invalidate all traffic going to your $TYPE_REF Provider.
                This endpoint should only be used if the current tokens are compromised.
                
                ---
            """.trimIndent()
        }
    }

    val retrieveSpecification = call("retrieveSpecification", ProvidersRetrieveSpecificationRequest.serializer(), ProvidersRetrieveSpecificationResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "specification", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Retrieves the specification of a Provider"
            description = """
                This endpoint is used by internal services to look up the contact information of a $TYPE_REF Provider.
            """.trimIndent()
        }
    }

    @Deprecated("Use the simpler register endpoint instead")
    val requestApproval = call("requestApproval", ProvidersRequestApprovalRequest.serializer(), ProvidersRequestApprovalResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "requestApproval", roles = Roles.PUBLIC)

        documentation {
            summary = "Used for the approval protocol"
            description = """
                This call is used as part of the approval protocol. View the example for more information.
            """.trimIndent()

            useCaseReference(registrationUseCase, "Registration protocol")
        }
    }

    @Deprecated("Use the simpler register endpoint instead")
    val approve = call("approve", ProvidersApproveRequest.serializer(), ProvidersApproveResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "approve", roles = Roles.PUBLIC)

        documentation {
            summary = "Used for the last step of the approval protocol"
            description = """
                This call is used as part of the approval protocol. View the example for more information.
            """.trimIndent()

            useCaseReference(registrationUseCase, "Registration protocol")
        }
    }
}
