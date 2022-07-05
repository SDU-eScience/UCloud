package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ProductPriceUnit
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.accounting.api.providers.SupportByProvider
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.singlePageOf
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiExampleValue
data class ExampleResource(
    override val id: String,
    override val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val updates: List<Update>,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions?
) : Resource<Product, ExampleResourceSupport> {
    @UCloudApiExampleValue
    @Serializable
    data class Spec(
        val start: Int,
        val target: Int,
        override val product: ProductReference,
    ) : ResourceSpecification

    @UCloudApiExampleValue
    @Serializable
    data class Update(
        override val timestamp: Long = 0L,
        override val status: String? = null,
        val newState: State? = null,
        val currentValue: Int? = null,
    ) : ResourceUpdate

    @UCloudApiExampleValue
    enum class State {
        PENDING,
        RUNNING,
        DONE,
    }


    @UCloudApiExampleValue
    @Serializable
    data class Status(
        val state: State,
        val value: Int,
        override var resolvedSupport: ResolvedSupport<Product, ExampleResourceSupport>? = null,
        override var resolvedProduct: Product? = null,
    ) : ResourceStatus<Product, ExampleResourceSupport>
}

@UCloudApiExampleValue
@Serializable
data class ExampleResourceSupport(
    override val product: ProductReference,
    val supportsBackwardsCounting: Supported = Supported.NOT_SUPPORTED,
) : ProductSupport {
    enum class Supported {
        SUPPORTED,
        NOT_SUPPORTED
    }
}

@UCloudApiExampleValue
@Serializable
data class ExampleResourceFlags(
    val filterState: ExampleResource.State? = null,
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
    override val hideProductId: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProvider: String? = null,
) : ResourceIncludeFlags

@UCloudApiExampleValue
private val simpleResource = ExampleResource(
    "1234",
    ExampleResource.Spec(
        0, 100,
        ProductReference("example-compute", "example-compute", "example")
    ),
    1635170395571L,
    ExampleResource.Status(ExampleResource.State.RUNNING, 10),
    listOf(
        ExampleResource.Update(
            1635170395571L,
            "We are about to start counting!",
            ExampleResource.State.PENDING
        ),
        ExampleResource.Update(
            1635170395571L,
            "We are now counting!",
            ExampleResource.State.RUNNING,
            10
        )
    ),
    ResourceOwner("user", null),
    ResourcePermissions(listOf(Permission.ADMIN), emptyList())
)

@OptIn(UCloudApiExampleValue::class)
typealias ExampleResourcesSuper = ResourceApi<ExampleResource, ExampleResource.Spec, ExampleResource.Update,
        ExampleResourceFlags, ExampleResource.Status, Product, ExampleResourceSupport>

@OptIn(UCloudApiExampleValue::class)
object Resources : ExampleResourcesSuper("example") {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        ExampleResource.serializer(),
        typeOfIfPossible<ExampleResource>(),
        ExampleResource.Spec.serializer(),
        typeOfIfPossible<ExampleResource.Spec>(),
        ExampleResource.Update.serializer(),
        typeOfIfPossible<ExampleResource.Update>(),
        ExampleResourceFlags.serializer(),
        typeOfIfPossible<ExampleResourceFlags>(),
        ExampleResource.Status.serializer(),
        typeOfIfPossible<ExampleResource.Status>(),
        ExampleResourceSupport.serializer(),
        typeOfIfPossible<ExampleResourceSupport>(),
        Product.serializer(),
        typeOfIfPossible<Product>(),
    )

    const val readMeFirst = """
        
__📝 NOTE:__ This API follows the standard Resources API. We recommend that you have already read and understood the
concepts described [here](/docs/developer-guide/orchestration/resources.md).
        
---

    """

    init {
        val Job = "$TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job"
        description = """
            Resources are the base abstraction used for orchestration in UCloud.

            In this article, we will take a closer look at what we mean when we say that UCloud is an orchestrator of 
            resources. Before you begin, we recommend that you have already read about:

            - [Providers](/docs/developer-guide/accounting-and-projects/providers.md): Exposes compute and storage 
              resources to end-users.
            - [Products](/docs/developer-guide/accounting-and-projects/products.md): Defines the services exposed by 
              providers.
            - [Wallets](/docs/developer-guide/accounting-and-projects/accounting/wallets.md): Holds allocations which 
              grant access to products.

            UCloud uses the resource abstraction to synchronize tasks between UCloud/Core and providers. As a result, 
            resources are often used to describe work for the provider. For example, a computational $Job is one type 
            of resource used in UCloud. 

            To understand how resources work, we will first examine what all resources have in common:

            - __A set of unique identifiers:__ Users and services can reference resources by using a unique ID.
            - __Product and provider reference:__ Most resources describe a work of a provider. As a result, these 
              resources must have a backing product.
            - __A resource specification:__ Describes the resource. For example, this could be the parameters of a 
              computational $Job.
            - __Ownership and permissions:__ All resources have exactly one 
              [workspace](/docs/developer-guide/accounting-and-projects/projects/projects.md) owner.
            - __Updates and status:__ Providers can send regular updates about a resource. These update describe 
              changes in the system. These changes in turn affect the current status.
              
            ## The Catalog
            
            UCloud, in almost all cases, store a record of all resources in use. We refer to this datastore as the 
            catalog of UCloud. As a result, UCloud/Core can fulfil some operations without involving the provider. 
            In particular, UCloud/Core performs many read operations without the provider's involvement.

            End-users interact with all resources through a standarized API. The API provides common CRUD operations 
            along with permission related operations. Concrete resources further extend this API with resource specific 
            tasks. For example, virtual machines expose an operation to shut down the machine. 

            On this page we will discuss the end-user API. But on the following pages, you can discover the siblings of 
            this API used by providers:

            - UCloud/Core invokes the Provider API to proxy information from the end-user API
            - The provider invokes the Control API to register changes in UCloud/Core

            ## The Permission Model

            UCloud uses a RBAC based permission model. As a workspace administrator, you must assign permissions for 
            resources to workspace groups.

            Currently, UCloud has the following permissions:

            - `READ`: Grants access to operations which return a resource
            - `EDIT`:  Grants access to operations which modify a resource
            - `ADMIN`: Grants access to privileged operations which read or modify a resource. Workspace administrators
              hold this permission. It is not possible to grant this permission through $CALL_REF example.updateAcl .
            - `PROVIDER`: Grants access to privileged operations which read or modify a resource. Implicit permission 
              granted to providers of a resource. It is not possible to grant this permission through
              $CALL_REF example.updateAcl .
              
            UCloud/Core checks all permissions before proxying information to the provider. However, this doesn't mean 
            that an operation must succeed once it reaches a provider. Providers can perform additional permission 
            checking once a request arrives.
            
            ## Feature Detection
            
            The resource API has support for feature detection. This happens through the 
            $CALL_REF example.retrieveProducts operation. Feature detection is specific to concrete resources. In 
            general terms, feature detection can change:

            - A provider might only support a subset of fields (of a data model).
            - Some operations might be optional. A provider can declare support for advanced features.
            - A provider might only support a subset of operation workloads. They can require work to follow a certain 
              structure. For example, a provider might declare support for containers but not for virtual machines. 
              
            ## A Note on the Examples

            In the examples, we will work with a simple resource, used only in examples. This resource instructs the 
            provider to count from one integer to another integer. The end-user specifies these numbers in the 
            specification. The provider communicates the progress through updates. End-users can read the current 
            progress from the status property.

            By default, all providers support counting "forward" (in the positive direction). However, providers must 
            declare that they support counting "backwards" (negative direction). If they do not declare support for 
            this, then UCloud/Core will reject all requests counting backwards. 
        """.trimIndent()
    }

    override fun documentation() {


        useCase(
            "browse",
            "Browsing the catalog",
            flow = {
                val user = basicUser()

                comment(
                    """
                    In this example, we will discover how a user can browse their catalog. This is done through the
                    browse operation. The browse operation exposes the results using the pagination API of UCloud.
                    
                    As we will see later, it is possible to filter in the results returned using the flags of the
                    operation.
                """.trimIndent()
                )

                success(
                    browse,
                    ResourceBrowseRequest(ExampleResourceFlags()),
                    PageV2(50, listOf(simpleResource), null),
                    user
                )

                comment(
                    """
                    📝 NOTE: The provider has already started counting. You can observe the changes which lead to the
                    current status through the updates.
                """.trimIndent()
                )
            }
        )

        useCase(
            "create",
            "Creating and retrieving a resource",
            flow = {
                val user = basicUser()

                comment(
                    """
                    In this example, we will discover how to create a resource and retrieve information about it.
                """.trimIndent()
                )

                success(
                    create!!,
                    bulkRequestOf(simpleResource.specification),
                    BulkResponse(listOf(FindByStringId("1234"))),
                    user
                )

                comment(
                    """
                    📝 NOTE: Users only specify the specification when they wish to create a resource. The specification
                    defines the values which are in the control of the user. The specification remains immutable
                    for the resource's lifetime. Mutable values are instead listed in the status.
                """.trimIndent()
                )

                success(
                    retrieve,
                    ResourceRetrieveRequest(ExampleResourceFlags(), "1234"),
                    simpleResource,
                    user
                )
            }
        )

        useCase(
            "filtering",
            "Browsing the catalog with a filter",
            flow = {
                val user = basicUser()

                comment(
                    """
                    In this example, we will look at the flags which are passed to both browse and retrieve operations.
                    This value is used to:
                    
                    - Filter out values: These properties are prefixed by filter* and remove results from the response.
                      When used in a retrieve operation, this will cause a 404 if no results are found.
                    - Include additional data: These properties are prefixed by include* and can be used to load 
                      additional data. This data is returned as part of the status object. The intention of these are to
                      save the client a round-trip by retrieving all relevant data in a single call.
                """.trimIndent()
                )

                success(
                    browse,
                    ResourceBrowseRequest(ExampleResourceFlags(filterState = ExampleResource.State.RUNNING)),
                    PageV2(50, listOf(simpleResource), null),
                    user
                )
            }
        )

        useCase(
            "search",
            "Searching for data",
            flow = {
                val user = basicUser()

                comment(
                    """
                    In this example, we will discover the search functionality of resources. Search allows for free-text
                    queries which attempts to find relevant results. This is very different from browse with filters, 
                    since 'relevancy' is a vaguely defined concept. Search is not guaranteed to return results in any
                    deterministic fashion, unlike browse.
                """.trimIndent()
                )

                comment("We start with the following dataset.")

                success(
                    browse,
                    ResourceBrowseRequest(ExampleResourceFlags(filterState = ExampleResource.State.RUNNING)),
                    PageV2(
                        50,
                        (1..3).map {
                            simpleResource.copy(
                                id = it.toString(),
                                specification = simpleResource.specification.copy(target = it * 100),
                                updates = emptyList()
                            )
                        },
                        null
                    ),
                    user
                )

                comment(
                    """
                    Search may look in many different fields to determine if a result is relevant. Searching for the
                    value 300 might produce the following results.
                """.trimIndent()
                )

                success(
                    search!!,
                    ResourceSearchRequest(
                        ExampleResourceFlags(),
                        "300"
                    ),
                    PageV2(
                        50,
                        listOf(
                            simpleResource.copy(
                                id = "3",
                                specification = simpleResource.specification.copy(target = 300),
                                updates = emptyList()
                            )
                        ),
                        null
                    ),
                    user
                )
            }
        )

        useCase(
            "feature_detection",
            "Feature detection (Supported)",
            flow = {
                val user = basicUser()
                val product = ProductReference("example-compute", "example-compute", "example")

                comment(
                    """
                    In this example, we will show how to use the feature detection feature of resources. Recall, that
                    providers need to specify if they support counting backwards.
                """.trimIndent()
                )

                success(
                    retrieveProducts,
                    Unit,
                    SupportByProvider(
                        mapOf(
                            "example" to listOf(
                                ResolvedSupport(
                                    Product.Compute(
                                        "example-compute",
                                        1L,
                                        ProductCategoryId("example-compute", "example"),
                                        "An example machine",
                                        cpu = 1,
                                        memoryInGigs = 1,
                                        unitOfPrice = ProductPriceUnit.UNITS_PER_HOUR
                                    ),
                                    ExampleResourceSupport(
                                        product,
                                        supportsBackwardsCounting = ExampleResourceSupport.Supported.SUPPORTED
                                    )
                                )
                            )
                        )
                    ),
                    user
                )

                comment("In this case, the provider supports counting backwards.")
                comment("Creating a resource which counts backwards should succeed.")

                success(
                    create!!,
                    bulkRequestOf(ExampleResource.Spec(0, -100, product)),
                    BulkResponse(listOf(FindByStringId("1234"))),
                    user
                )
            }
        )

        useCase(
            "feature_detection_failure",
            "Feature detection (Failure scenario)",
            flow = {
                val user = basicUser()
                val product = ProductReference("example-compute", "example-compute", "example")

                comment(
                    """
                    In this example, we will show how to use the feature detection feature of resources. Recall, that
                    providers need to specify if they support counting backwards.
                """.trimIndent()
                )

                success(
                    retrieveProducts,
                    Unit,
                    SupportByProvider(
                        mapOf(
                            "example" to listOf(
                                ResolvedSupport(
                                    Product.Compute(
                                        "example-compute",
                                        1L,
                                        ProductCategoryId("example-compute", "example"),
                                        "An example machine",
                                        cpu = 1,
                                        memoryInGigs = 1,
                                        unitOfPrice = ProductPriceUnit.UNITS_PER_HOUR
                                    ),
                                    ExampleResourceSupport(
                                        product,
                                        supportsBackwardsCounting = ExampleResourceSupport.Supported.NOT_SUPPORTED
                                    )
                                )
                            )
                        )
                    ),
                    user
                )

                comment("In this case, the provider does not support counting backwards.")
                comment("Creating a resource which counts backwards should fail.")

                failure(
                    create!!,
                    bulkRequestOf(ExampleResource.Spec(0, -100, product)),
                    CommonErrorMessage("Backwards counting is not supported"),
                    HttpStatusCode.BadRequest,
                    user
                )
            }
        )

        useCase(
            "collaboration",
            "Resource Collaboration",
            flow = {
                val alice = actor("alice", "A UCloud user named Alice")
                val bob = actor("bob", "A UCloud user named Bob")

                comment(
                    """
                    In this example, we discover how to use the resource collaboration features of UCloud. This example
                    involves two users: Alice and Bob.
                """.trimIndent()
                )

                comment("Alice starts by creating a resource")

                success(
                    create!!,
                    bulkRequestOf(simpleResource.specification),
                    BulkResponse(listOf(FindByStringId("1234"))),
                    alice
                )

                comment("By default, Bob doesn't have access to this resource. Attempting to retrieve it will fail.")

                failure(
                    retrieve,
                    ResourceRetrieveRequest(ExampleResourceFlags(), "1234"),
                    CommonErrorMessage("Unknown resource. It might not exist or you might be lacking permissions."),
                    HttpStatusCode.NotFound,
                    bob
                )

                comment(
                    "Alice can change the permissions of the resource by invoking updateAcl. " +
                            "This causes Bob to gain READ permissions."
                )

                success(
                    updateAcl,
                    bulkRequestOf(
                        UpdatedAcl(
                            "1234",
                            listOf(
                                ResourceAclEntry(
                                    AclEntity.ProjectGroup("Project", "Group of Bob"),
                                    listOf(Permission.READ)
                                )
                            ),
                            emptyList()
                        )
                    ),
                    BulkResponse(listOf(Unit)),
                    alice
                )

                comment("Bob can now retrieve the resource.")

                success(
                    retrieve,
                    ResourceRetrieveRequest(ExampleResourceFlags(), "1234"),
                    simpleResource.copy(permissions = ResourcePermissions(listOf(Permission.READ), emptyList())),
                    bob
                )
            }
        )
    }

    override val create = super.create!!
    override val delete = super.delete!!
    override val search = super.search!!
}

@UCloudApiExampleValue
object ResourceProvider : ResourceProviderApi<ExampleResource, ExampleResource.Spec, ExampleResource.Update,
        ExampleResourceFlags, ExampleResource.Status, Product, ExampleResourceSupport>("example", "PROVIDERID") {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        ExampleResource.serializer(),
        typeOfIfPossible<ExampleResource>(),
        ExampleResource.Spec.serializer(),
        typeOfIfPossible<ExampleResource.Spec>(),
        ExampleResource.Update.serializer(),
        typeOfIfPossible<ExampleResource.Update>(),
        ExampleResourceFlags.serializer(),
        typeOfIfPossible<ExampleResourceFlags>(),
        ExampleResource.Status.serializer(),
        typeOfIfPossible<ExampleResource.Status>(),
        ExampleResourceSupport.serializer(),
        typeOfIfPossible<ExampleResourceSupport>(),
        Product.serializer(),
        typeOfIfPossible<Product>(),
    )

    init {
        description = """
Providers deal almost exclusively with UCloud through resource provider APIs.

We have already told you about the end-user APIs for resources. UCloud uses resources to synchronize work between 
UCloud/Core and the provider. We achieve this synchronization through two different APIs:

- __The ingoing API (Provider)__: This API handles requests, ultimately, from the end-user. UCloud/Core proxies the 
  information from the end-user. During the proxy-step, UCloud/Core performs validation, authentication, authorization 
  and auditing. 
- __The outgoing API (Control):__ The outgoing API is the provider's chance to send requests back to UCloud/Core. 
  For example, we use this API for: auditing, updates and queries about the catalog. 

In this document, we will cover the ingoing API. This API, in most cases, mirrors the end-user API for write 
operations. UCloud expands the API by replacing most request types with a fully-qualified form. This means we replace 
specifications and references with full resource objects.

## A Note on the Examples

The examples in this section follow the same scenario as the end-user API.
        """.trimIndent()
    }

    override fun documentation() {
        useCase(
            "simple_create",
            "Creation of Resources",
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                comment(
                    """
                    In this example, we show a simple creation request. The creation request is always initiated by a 
                    user.
                """.trimIndent()
                )

                success(
                    create,
                    bulkRequestOf(simpleResource),
                    bulkResponseOf(null),
                    ucloud
                )

                comment("In this case, the provider decided not to attach a provider generated ID.")
                comment("The provider can, at a later point in time, retrieve this resource from UCloud/Core.")

                success(
                    ResourceControl.retrieve,
                    ResourceRetrieveRequest(ExampleResourceFlags(), simpleResource.id),
                    simpleResource,
                    provider
                )
            }
        )

        useCase(
            "generated_id",
            "Looking up resources by provider generated ID",
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                success(
                    create,
                    bulkRequestOf(simpleResource),
                    bulkResponseOf(FindByStringId("mhxas1")),
                    ucloud
                )

                success(
                    ResourceControl.browse,
                    ResourceBrowseRequest(ExampleResourceFlags(filterProviderIds = "mhxas1")),
                    singlePageOf(simpleResource),
                    provider
                )
            }
        )

        useCase(
            "create_failure",
            "Dealing with failures",
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                failure(
                    create,
                    bulkRequestOf(simpleResource),
                    CommonErrorMessage("The counting engines failed to start"),
                    HttpStatusCode.InternalServerError,
                    ucloud
                )
            }
        )

        useCase(
            "create_failure",
            "Dealing with partial failures",
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                comment("In this example, we will discover how a provider should deal with a partial failure.")

                failure(
                    create,
                    bulkRequestOf(simpleResource, simpleResource.copy(id = "51214")),
                    CommonErrorMessage("The counting engines failed to start"),
                    HttpStatusCode.InternalServerError,
                    ucloud
                )

                comment(
                    """
                    In this case, imagine that the provider failed to create the second resource. This should
                    immediately trigger cleanup on the provider, if the first resource was already created. The provider
                    should then respond with an appropriate error message. Providers should not attempt to only
                    partially create the resources.
                """.trimIndent()
                )
            }
        )
    }

    override val delete = super.delete!!
}

@UCloudApiExampleValue
object ResourceControl : ResourceControlApi<ExampleResource, ExampleResource.Spec, ExampleResource.Update,
        ExampleResourceFlags, ExampleResource.Status, Product, ExampleResourceSupport>("example") {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        ExampleResource.serializer(),
        typeOfIfPossible<ExampleResource>(),
        ExampleResource.Spec.serializer(),
        typeOfIfPossible<ExampleResource.Spec>(),
        ExampleResource.Update.serializer(),
        typeOfIfPossible<ExampleResource.Update>(),
        ExampleResourceFlags.serializer(),
        typeOfIfPossible<ExampleResourceFlags>(),
        ExampleResource.Status.serializer(),
        typeOfIfPossible<ExampleResource.Status>(),
        ExampleResourceSupport.serializer(),
        typeOfIfPossible<ExampleResourceSupport>(),
        Product.serializer(),
        typeOfIfPossible<Product>(),
    )
}
