package dk.sdu.cloud.accounting.api.providers

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiOwnedBy(Resources::class)
@UCloudApiStable
data class ResourceChargeCredits(
    @UCloudApiDoc("The ID of the `Resource`")
    val id: String,

    @UCloudApiDoc(
        "The ID of the charge\n\n" +
            "This charge ID must be unique for the `Resource`, UCloud will reject charges which are not unique."
    )
    val chargeId: String,

    @UCloudApiDoc("Amount of units to charge the user")
    val units: Long,

    val periods: Long = 1L,

    val performedBy: String? = null,

    val description: String? = null,
)

@Serializable
@UCloudApiOwnedBy(Resources::class)
@UCloudApiStable
data class ResourceChargeCreditsResponse(
    @UCloudApiDoc(
        "A list of resources which could not be charged due to lack of funds. " +
            "If all resources were charged successfully then this will empty."
    )
    val insufficientFunds: List<FindByStringId>,

    @UCloudApiDoc(
        "A list of resources which could not be charged due to it being a duplicate charge. " +
            "If all resources were charged successfully this will be empty."
    )
    val duplicateCharges: List<FindByStringId>,
)

@Serializable
data class ProviderRegisteredResource<Spec : ResourceSpecification>(
    val spec: Spec,
    val providerGeneratedId: String? = null,
    val createdBy: String? = null,
    val project: String? = null,

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc("Grants full read access to all project members. The project property must be non-null.")
    val projectAllRead: Boolean = false,

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc("Grants full write access to all project members. The project property must be non-null.")
    val projectAllWrite: Boolean = false,
) {
    init {
        if (projectAllRead && project == null) {
            throw RPCException("project must be specified when projectAllRead = true", HttpStatusCode.BadRequest)
        }

        if (projectAllWrite && project == null) {
            throw RPCException("project must be specified when projectAllWrite = true", HttpStatusCode.BadRequest)
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
@TSSkipCodegen
abstract class ResourceControlApi<
    Res : Resource<Prod, Support>,
    Spec : ResourceSpecification,
    Update : ResourceUpdate,
    Flags : ResourceIncludeFlags,
    Status : ResourceStatus<Prod, Support>,
    Prod : Product,
    Support : ProductSupport>(namespace: String) : CallDescriptionContainer("$namespace.control") {
    val baseContext = "/api/${namespace.replace(".", "/")}/control"

    abstract val typeInfo: ResourceTypeInfo<Res, Spec, Update, Flags, Status, Prod, Support>

    val retrieve: CallDescription<ResourceRetrieveRequest<Flags>, Res, CommonErrorMessage>
        get() = call(
            name = "retrieve",
            handler = {
                httpRetrieve(
                    ResourceRetrieveRequest.serializer(typeInfo.flagsSerializer),
                    typeOfIfPossible<ResourceRetrieveRequest<Flags>>(),
                    baseContext,
                    roles = Roles.PROVIDER
                )
            },
            requestType = ResourceRetrieveRequest.serializer(typeInfo.flagsSerializer),
            successType = typeInfo.resSerializer,
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<ResourceRetrieveRequest<Flags>>(),
            successClass = typeInfo.resType,
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )

    val browse: CallDescription<ResourceBrowseRequest<Flags>, PageV2<Res>, CommonErrorMessage>
        get() = call(
            name = "browse",
            handler = {
                httpBrowse(
                    ResourceBrowseRequest.serializer(typeInfo.flagsSerializer),
                    typeOfIfPossible<ResourceBrowseRequest<Flags>>(),
                    baseContext,
                    roles = Roles.PROVIDER
                )
            },
            requestType = ResourceBrowseRequest.serializer(typeInfo.flagsSerializer),
            successType = PageV2.serializer(typeInfo.resSerializer),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<ResourceBrowseRequest<Flags>>(),
            successClass = typeOfIfPossible<PageV2<Res>>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )


    val update: CallDescription<BulkRequest<ResourceUpdateAndId<Update>>, Unit, CommonErrorMessage>
        get() = call(
            name = "update",
            handler = {
                httpUpdate(
                    BulkRequest.serializer(ResourceUpdateAndId.serializer(typeInfo.updateSerializer)),
                    baseContext,
                    "update",
                    roles = Roles.PROVIDER
                )
            },
            requestType = BulkRequest.serializer(ResourceUpdateAndId.serializer(typeInfo.updateSerializer)),
            successType = Unit.serializer(),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<BulkRequest<ResourceUpdateAndId<Update>>>(),
            successClass = typeOfIfPossible<Unit>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>(),
        )

    val checkCredits: CallDescription<BulkRequest<ResourceChargeCredits>, ResourceChargeCreditsResponse,
            CommonErrorMessage>
        get() = call(
            name = "checkCredits",
            handler = {
                httpUpdate(
                    BulkRequest.serializer(ResourceChargeCredits.serializer()),
                    baseContext,
                    "checkCredits",
                    roles = Roles.PROVIDER
                )
            },
            requestType = BulkRequest.serializer(ResourceChargeCredits.serializer()),
            successType = ResourceChargeCreditsResponse.serializer(),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<BulkRequest<ResourceChargeCredits>>(),
            successClass = typeOfIfPossible<ResourceChargeCreditsResponse>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )

    val chargeCredits: CallDescription<BulkRequest<ResourceChargeCredits>, ResourceChargeCreditsResponse,
        CommonErrorMessage>
        get() = call(
            name = "chargeCredits",
            handler = {
                httpUpdate(
                    BulkRequest.serializer(ResourceChargeCredits.serializer()),
                    baseContext,
                    "chargeCredits",
                    roles = Roles.PROVIDER
                )
            },
            requestType = BulkRequest.serializer(ResourceChargeCredits.serializer()),
            successType = ResourceChargeCreditsResponse.serializer(),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<BulkRequest<ResourceChargeCredits>>(),
            successClass = typeOfIfPossible<ResourceChargeCreditsResponse>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )

    val register: CallDescription<BulkRequest<ProviderRegisteredResource<Spec>>, BulkResponse<FindByStringId>,
        CommonErrorMessage>
        get() = call(
            name = "register",
            handler = {
                httpUpdate(
                    BulkRequest.serializer(ProviderRegisteredResource.serializer(typeInfo.specSerializer)),
                    baseContext,
                    "register",
                    roles = Roles.PROVIDER
                )
            },
            requestType = BulkRequest.serializer(ProviderRegisteredResource.serializer(typeInfo.specSerializer)),
            successType = BulkResponse.serializer(FindByStringId.serializer()),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<BulkRequest<Spec>>(),
            successClass = typeOfIfPossible<BulkResponse<FindByStringId>>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )
}
