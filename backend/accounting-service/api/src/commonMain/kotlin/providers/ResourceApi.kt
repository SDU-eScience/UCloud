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

@Serializable
data class ResourceBrowseRequest<Flags : ResourceIncludeFlags>(
    val flags: Flags,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

@Serializable
data class ResourceSearchRequest<Flags : ResourceIncludeFlags>(
    val query: String,
    val flags: Flags,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

@Serializable
data class ResourceRetrieveRequest<Flags : ResourceIncludeFlags>(
    val id: String,
    val flags: Flags,
)

@Serializable
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
inline fun <
    reified Res : Resource<Prod, Support>,
    reified Spec : ResourceSpecification,
    reified Update : ResourceUpdate,
    reified Flags : ResourceIncludeFlags,
    reified Status : ResourceStatus<Prod, Support>,
    reified Prod : Product,
    reified Support : ProductSupport
    > ResourceTypeInfo(): ResourceTypeInfo<Res, Spec, Update, Flags, Status, Prod, Support> {
    return ResourceTypeInfo(
        serializer(),
        typeOf<Res>(),
        serializer(),
        typeOf<Spec>(),
        serializer(),
        typeOf<Update>(),
        serializer(),
        typeOf<Flags>(),
        serializer(),
        typeOf<Status>(),
        serializer(),
        typeOf<Support>(),
        serializer(),
        typeOf<Prod>()
    )
}

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

    val browse: CallDescription<ResourceBrowseRequest<Flags>, PageV2<Res>, CommonErrorMessage>
        get() = call(
            name = "browse",
            handler = {
                httpBrowse(
                    ResourceBrowseRequest.serializer(typeInfo.flagsSerializer),
                    typeOf<ResourceBrowseRequest<Flags>>(),
                    baseContext
                )
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
            },
            requestType = ResourceRetrieveRequest.serializer(typeInfo.flagsSerializer),
            successType = typeInfo.resSerializer,
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<ResourceRetrieveRequest<Flags>>(),
            successClass = typeInfo.resType,
            errorClass = typeOf<CommonErrorMessage>()
        )

    val create: CallDescription<BulkRequest<Spec>, BulkResponse<FindByStringId?>, CommonErrorMessage>
        get() = call(
            name = "create",
            handler = {
                httpCreate(BulkRequest.serializer(typeInfo.specSerializer), baseContext)
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
            },
            requestType = ResourceSearchRequest.serializer(typeInfo.flagsSerializer),
            successType = PageV2.serializer(typeInfo.resSerializer),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<ResourceSearchRequest<Flags>>(),
            successClass = typeOf<PageV2<Res>>(),
            errorClass = typeOf<CommonErrorMessage>()
        )
}
