package dk.sdu.cloud.accounting.api.providers

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Suppress("EnumEntryName")
@UCloudApiOwnedBy(Resources::class)
// TODO(Dan): This should probably be moved to `dk.sdu.cloud` as it is of general use.
enum class SortDirection {
    ascending,
    descending
}

interface SortFlags {
    val sortBy: String?
    val sortDirection: SortDirection?
}

@Serializable
@UCloudApiOwnedBy(Resources::class)
data class ResourceBrowseRequest<Flags : ResourceIncludeFlags>(
    val flags: Flags,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
    override val sortBy: String? = null,
    override val sortDirection: SortDirection? = SortDirection.ascending,
) : WithPaginationRequestV2, SortFlags

@Serializable
@UCloudApiOwnedBy(Resources::class)
data class ResourceSearchRequest<Flags : ResourceIncludeFlags>(
    val flags: Flags,
    val query: String,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
    override val sortBy: String? = null,
    override val sortDirection: SortDirection? = SortDirection.ascending,
) : WithPaginationRequestV2, SortFlags

@Serializable
@UCloudApiOwnedBy(Resources::class)
data class ResourceRetrieveRequest<Flags : ResourceIncludeFlags>(
    val flags: Flags,
    val id: String,
)

@Serializable
@UCloudApiOwnedBy(Resources::class)
data class SupportByProvider<P : Product, S : ProductSupport>(
    val productsByProvider: Map<String, List<ResolvedSupport<P, S>>>
)

data class ResourceTypeInfo<
    Res : Resource<Prod, Support>,
    Spec : ResourceSpecification,
    Update : ResourceUpdate,
    Flags : ResourceIncludeFlags,
    Status : ResourceStatus<Prod, Support>,
    Prod : Product,
    Support : ProductSupport>(
    val resSerializer: KSerializer<Res>,
    val resType: KType,
    val specSerializer: KSerializer<Spec>,
    val specType: KType,
    val updateSerializer: KSerializer<Update>,
    val updateType: KType,
    val flagsSerializer: KSerializer<Flags>,
    val flagsType: KType,
    val statusSerializer: KSerializer<Status>,
    val statusType: KType,
    val supportSerializer: KSerializer<Support>,
    val supportType: KType,
    val productSerializer: KSerializer<Prod>,
    val productType: KType,
)

@OptIn(ExperimentalStdlibApi::class)
@TSSkipCodegen
abstract class ResourceApi<
    Res : Resource<Prod, Support>,
    Spec : ResourceSpecification,
    Update : ResourceUpdate,
    Flags : ResourceIncludeFlags,
    Status : ResourceStatus<Prod, Support>,
    Prod : Product,
    Support : ProductSupport>(
    namespace: String
) : CallDescriptionContainer(namespace) {
    val baseContext: String = "/api/${namespace.replace(".", "/")}"

    abstract val typeInfo: ResourceTypeInfo<Res, Spec, Update, Flags, Status, Prod, Support>

    val init: CallDescription<Unit, Unit, CommonErrorMessage>
        get() = call(
            name = "init",
            handler = {
                httpUpdate(
                    Unit.serializer(),
                    baseContext,
                    "init"
                )

                documentation {
                    summary = "Request (potential) initialization of resources"
                    description = """
                        This request is sent by the client, if the client believes that initialization of resources 
                        might be needed. NOTE: This request might be sent even if initialization has already taken 
                        place. UCloud/Core does not check if initialization has already taken place, it simply validates
                        the request.
                    """.trimIndent()
                }
            },
            requestType = Unit.serializer(),
            successType = Unit.serializer(),
            errorType =  CommonErrorMessage.serializer(),
            requestClass = typeOf<Unit>(),
            successClass = typeOf<Unit>(),
            errorClass = typeOf<CommonErrorMessage>()
        )

    val browse: CallDescription<ResourceBrowseRequest<Flags>, PageV2<Res>, CommonErrorMessage>
        get() = call(
            name = "browse",
            handler = {
                httpBrowse(
                    ResourceBrowseRequest.serializer(typeInfo.flagsSerializer),
                    typeOf<ResourceBrowseRequest<Flags>>(),
                    baseContext
                )

                documentation {
                    summary = "Browses the catalog of available resources"
                }
            },
            requestType = ResourceBrowseRequest.serializer(typeInfo.flagsSerializer),
            successType = PageV2.serializer(typeInfo.resSerializer),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<ResourceBrowseRequest<Flags>>(),
            successClass = typeOf<PageV2<Res>>(),
            errorClass = typeOf<CommonErrorMessage>()
        )


    val retrieve: CallDescription<ResourceRetrieveRequest<Flags>, Res, CommonErrorMessage>
        get() = call(
            name = "retrieve",
            handler = {
                httpRetrieve(
                    ResourceRetrieveRequest.serializer(typeInfo.flagsSerializer),
                    typeOf<ResourceRetrieveRequest<Flags>>(),
                    baseContext
                )

                documentation {
                    summary = "Retrieve a single resource"
                }
            },
            requestType = ResourceRetrieveRequest.serializer(typeInfo.flagsSerializer),
            successType = typeInfo.resSerializer,
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<ResourceRetrieveRequest<Flags>>(),
            successClass = typeInfo.resType,
            errorClass = typeOf<CommonErrorMessage>()
        )

    open val create: CallDescription<BulkRequest<Spec>, BulkResponse<FindByStringId?>, CommonErrorMessage>?
        get() = call(
            name = "create",
            handler = {
                httpCreate(BulkRequest.serializer(typeInfo.specSerializer), baseContext)

                documentation {
                    summary = "Creates one or more resources"
                }
            },
            requestType = BulkRequest.serializer(typeInfo.specSerializer),
            successType = BulkResponse.serializer(FindByStringId.serializer().nullable),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<BulkRequest<Spec>>(),
            successClass = typeOf<BulkResponse<FindByStringId>>(),
            errorClass = typeOf<CommonErrorMessage>()
        )

    open val delete: CallDescription<BulkRequest<FindByStringId>, BulkResponse<Unit?>, CommonErrorMessage>?
        get() = call(
            name = "delete",
            handler = {
                httpDelete(BulkRequest.serializer(FindByStringId.serializer()), baseContext)

                documentation {
                    summary = "Deletes one or more resources"
                }
            },
            requestType = BulkRequest.serializer(FindByStringId.serializer()),
            successType = BulkResponse.serializer(Unit.serializer().nullable),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<BulkRequest<FindByStringId>>(),
            successClass = typeOf<BulkResponse<Unit?>>(),
            errorClass = typeOf<CommonErrorMessage>()
        )

    val retrieveProducts: CallDescription<Unit, SupportByProvider<Prod, Support>, CommonErrorMessage>
        get() = call(
            name = "retrieveProducts",
            handler = {
                httpRetrieve(baseContext, "products")

                documentation {
                    summary = "Retrieve product support for all accessible providers"
                    description = """
                        This endpoint will determine all providers that which the authenticated user has access to, in
                        the current workspace. A user has access to a product, and thus a provider, if the product is
                        either free or if the user has been granted credits to use the product.
                        
                        See also:
                        
                        - $TYPE_REF dk.sdu.cloud.accounting.api.Product
                        - [Grants](/docs/developer-guide/accounting-and-projects/grants/grants.md)
                    """.trimIndent()
                }
            },
            requestType = Unit.serializer(),
            successType = SupportByProvider.serializer(typeInfo.productSerializer, typeInfo.supportSerializer),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<Unit>(),
            successClass = typeOf<SupportByProvider<Prod, Support>>(),
            errorClass = typeOf<CommonErrorMessage>()
        )

    val updateAcl: CallDescription<BulkRequest<UpdatedAcl>, BulkResponse<Unit?>, CommonErrorMessage>
        get() = call(
            name = "updateAcl",
            handler = {
                httpUpdate(
                    BulkRequest.serializer(UpdatedAcl.serializer()),
                    baseContext,
                    "updateAcl",
                )

                documentation {
                    summary = "Updates the ACL attached to a resource"
                }
            },
            requestType = BulkRequest.serializer(UpdatedAcl.serializer()),
            successType = BulkResponse.serializer(Unit.serializer().nullable),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<BulkRequest<UpdatedAcl>>(),
            successClass = typeOf<BulkResponse<Unit?>>(),
            errorClass = typeOf<CommonErrorMessage>()
        )

    open val search: CallDescription<ResourceSearchRequest<Flags>, PageV2<Res>, CommonErrorMessage>?
        get() = call(
            name = "search",
            handler = {
                httpSearch(
                    ResourceSearchRequest.serializer(typeInfo.flagsSerializer),
                    baseContext
                )

                documentation {
                    summary = "Searches the catalog of available resources"
                }
            },
            requestType = ResourceSearchRequest.serializer(typeInfo.flagsSerializer),
            successType = PageV2.serializer(typeInfo.resSerializer),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<ResourceSearchRequest<Flags>>(),
            successClass = typeOf<PageV2<Res>>(),
            errorClass = typeOf<CommonErrorMessage>()
        )
}
