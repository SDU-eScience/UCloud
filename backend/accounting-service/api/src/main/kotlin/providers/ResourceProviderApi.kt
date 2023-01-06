package dk.sdu.cloud.accounting.api.providers

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
data class ProviderProducts<Support : ProductSupport>(
    val products: List<Support>,
)

@Serializable
@UCloudApiOwnedBy(Resources::class)
@UCloudApiStable
data class ResourceInitializationRequest(
    val principal: ResourceOwner,
)

@OptIn(ExperimentalStdlibApi::class)
@TSSkipCodegen
abstract class ResourceProviderApi<
    Res : Resource<Prod, Support>,
    Spec : ResourceSpecification,
    Update : ResourceUpdate,
    Flags : ResourceIncludeFlags,
    Status : ResourceStatus<Prod, Support>,
    Prod : Product,
    Support : ProductSupport>(
    namespace: String,
    provider: String
) : CallDescriptionContainer("$namespace.provider.$provider") {
    val baseContext = "/ucloud/$provider/${namespace.replace(".", "/")}"

    abstract val typeInfo: ResourceTypeInfo<Res, Spec, Update, Flags, Status, Prod, Support>

    val init: CallDescription<ResourceInitializationRequest, Unit, CommonErrorMessage>
        get() = call(
            name = "init",
            handler = {
                httpUpdate(
                    ResourceInitializationRequest.serializer(),
                    baseContext,
                    "init",
                    roles = Roles.PRIVILEGED
                )

                documentation {
                    summary = "Request from the user to (potentially) initialize any resources"
                    description = """
                        This request is sent by the client, if the client believes that initialization of resources 
                        might be needed. NOTE: This request might be sent even if initialization has already taken 
                        place. UCloud/Core does not check if initialization has already taken place, it simply validates
                        the request.
                    """.trimIndent()
                }
            },
            requestType = ResourceInitializationRequest.serializer(),
            successType = Unit.serializer(),
            errorType =  CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<ResourceInitializationRequest>(),
            successClass = typeOfIfPossible<Unit>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )


    val create: CallDescription<BulkRequest<Res>, BulkResponse<FindByStringId?>, CommonErrorMessage>
        get() = call(
            name = "create",
            handler = {
                httpCreate(BulkRequest.serializer(typeInfo.resSerializer), baseContext, roles = Roles.PRIVILEGED)
            },
            requestType = BulkRequest.serializer(typeInfo.resSerializer),
            successType = BulkResponse.serializer(FindByStringId.serializer().nullable),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<BulkRequest<Res>>(),
            successClass = typeOfIfPossible<BulkResponse<FindByStringId>>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )

    open val delete: CallDescription<BulkRequest<Res>, BulkResponse<Unit?>, CommonErrorMessage>?
        get() = call(
            name = "delete",
            handler = {
                httpDelete(BulkRequest.serializer(typeInfo.resSerializer), baseContext, roles = Roles.PRIVILEGED)
            },
            requestType = BulkRequest.serializer(typeInfo.resSerializer),
            successType = BulkResponse.serializer(Unit.serializer().nullable),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<BulkRequest<Res>>(),
            successClass = typeOfIfPossible<BulkResponse<Unit?>>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )

    val verify: CallDescription<BulkRequest<Res>, Unit, CommonErrorMessage>
        get() = call(
            name = "verify",
            handler = {
                httpUpdate(BulkRequest.serializer(typeInfo.resSerializer), baseContext, "verify", roles = Roles.PRIVILEGED)

                documentation {
                    summary = "Invoked by UCloud/Core to trigger verification of a single batch"
                    description = """
                        This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
                        Provider should immediately determine if these are still valid and recognized by the Provider.
                        If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
                        an update for each affected resource.
                    """.trimIndent()
                }
            },
            requestType = BulkRequest.serializer(typeInfo.resSerializer),
            successType = Unit.serializer(),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<BulkRequest<Res>>(),
            successClass = typeOfIfPossible<Unit>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )

    val retrieveProducts: CallDescription<Unit, BulkResponse<Support>, CommonErrorMessage>
        get() = call(
            name = "retrieveProducts",
            handler = {
                httpRetrieve(Unit.serializer(), typeOfIfPossible<Unit>(), baseContext, "products", roles = Roles.PRIVILEGED)

                documentation {
                    summary = "Retrieve product support for this providers"
                    description = """
                        This endpoint responds with the $TYPE_REF dk.sdu.cloud.accounting.api.Product s supported by
                        this provider along with details for how $TYPE_REF dk.sdu.cloud.accounting.api.Product is
                        supported. The $TYPE_REF dk.sdu.cloud.accounting.api.Product s must be registered with
                        UCloud/Core already.
                    """.trimIndent()
                }
            },
            requestType = Unit.serializer(),
            successType = BulkResponse.serializer(typeInfo.supportSerializer),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<Unit>(),
            successClass = typeOfIfPossible<BulkResponse<Support>>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )

    val updateAcl: CallDescription<BulkRequest<UpdatedAclWithResource<Res>>, BulkResponse<Unit?>, CommonErrorMessage>
        get() = call(
            name = "updateAcl",
            handler = {
                httpUpdate(
                    BulkRequest.serializer(UpdatedAclWithResource.serializer(typeInfo.resSerializer)),
                    baseContext,
                    "updateAcl",
                    roles = Roles.PRIVILEGED
                )

                documentation {
                    summary = "Callback received by the Provider when permissions are updated"
                    description = """
                        This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
                        internal state, then they may simply ignore this request by responding with `200 OK`. The
                        Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
                        not acknowledge the request.
                    """.trimIndent()
                }
            },
            requestType = BulkRequest.serializer(UpdatedAclWithResource.serializer(typeInfo.resSerializer)),
            successType = BulkResponse.serializer(Unit.serializer().nullable),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOfIfPossible<BulkRequest<UpdatedAclWithResource<Res>>>(),
            successClass = typeOfIfPossible<BulkResponse<Unit?>>(),
            errorClass = typeOfIfPossible<CommonErrorMessage>()
        )
}
