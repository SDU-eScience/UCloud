package dk.sdu.cloud.accounting.api.providers

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data class ProviderProducts<Support : ProductSupport>(
    val products: List<Support>,
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

    val create: CallDescription<BulkRequest<Res>, BulkResponse<FindByStringId?>, CommonErrorMessage>
        get() = call(
            name = "create",
            handler = {
                httpCreate(BulkRequest.serializer(typeInfo.resSerializer), baseContext, roles = Roles.PRIVILEGED)
            },
            requestType = BulkRequest.serializer(typeInfo.resSerializer),
            successType = BulkResponse.serializer(FindByStringId.serializer().nullable),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<BulkRequest<Res>>(),
            successClass = typeOf<BulkResponse<FindByStringId>>(),
            errorClass = typeOf<CommonErrorMessage>()
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
            requestClass = typeOf<BulkRequest<Res>>(),
            successClass = typeOf<BulkResponse<Unit?>>(),
            errorClass = typeOf<CommonErrorMessage>()
        )

    val verify: CallDescription<BulkRequest<Res>, Unit, CommonErrorMessage>
        get() = call(
            name = "verify",
            handler = {
                httpUpdate(BulkRequest.serializer(typeInfo.resSerializer), baseContext, "verify", roles = Roles.PRIVILEGED)
            },
            requestType = BulkRequest.serializer(typeInfo.resSerializer),
            successType = Unit.serializer(),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<BulkRequest<Res>>(),
            successClass = typeOf<Unit>(),
            errorClass = typeOf<CommonErrorMessage>()
        )

    val retrieveProducts: CallDescription<Unit, BulkResponse<Support>, CommonErrorMessage>
        get() = call(
            name = "retrieveProducts",
            handler = {
                httpRetrieve(Unit.serializer(), typeOf<Unit>(), baseContext, "products", roles = Roles.PRIVILEGED)
            },
            requestType = Unit.serializer(),
            successType = BulkResponse.serializer(typeInfo.supportSerializer),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<Unit>(),
            successClass = typeOf<BulkResponse<Support>>(),
            errorClass = typeOf<CommonErrorMessage>()
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
            },
            requestType = BulkRequest.serializer(UpdatedAclWithResource.serializer(typeInfo.resSerializer)),
            successType = BulkResponse.serializer(Unit.serializer().nullable),
            errorType = CommonErrorMessage.serializer(),
            requestClass = typeOf<BulkRequest<UpdatedAclWithResource<Res>>>(),
            successClass = typeOf<BulkResponse<Unit?>>(),
            errorClass = typeOf<CommonErrorMessage>()
        )
}