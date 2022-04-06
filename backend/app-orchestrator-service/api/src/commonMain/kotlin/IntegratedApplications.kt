package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceOwner
import kotlinx.serialization.*
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

// End-user API
// ====================================================================================================================

abstract class IntegratedApplicationApi<ConfigType>(
    namespace: String
) : CallDescriptionContainer("iapps.$namespace") {
    val baseContext = "/api/iapps/$namespace"

    abstract val configType: KType
    abstract val configSerializer: KSerializer<ConfigType>

    val retrieveConfiguration: CallDescription<
        IAppsRetrieveConfigRequest<ConfigType>,
        IAppsRetrieveConfigResponse<ConfigType>,
        CommonErrorMessage
    > get() = call(
        name = "retrieveConfiguration",
        handler = {
            httpRetrieve(
                IAppsRetrieveConfigRequest.serializer(configSerializer),
                typeOf<IAppsRetrieveConfigRequest<ConfigType>>(),
                baseContext,
            )
        },

        requestType = IAppsRetrieveConfigRequest.serializer(configSerializer),
        requestClass = typeOf<IAppsRetrieveConfigRequest<ConfigType>>(),

        successType = IAppsRetrieveConfigResponse.serializer(configSerializer),
        successClass = typeOf<IAppsRetrieveConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOf<CommonErrorMessage>(),
    )

    val updateConfiguration: CallDescription<
        IAppsUpdateConfigRequest<ConfigType>,
        IAppsUpdateConfigResponse<ConfigType>,
        CommonErrorMessage
    > get() = call(
        name = "updateConfiguration",
        handler = {
            httpUpdate(
                IAppsUpdateConfigRequest.serializer(configSerializer),
                baseContext,
                "update",
            )
        },

        requestType = IAppsUpdateConfigRequest.serializer(configSerializer),
        requestClass = typeOf<IAppsUpdateConfigRequest<ConfigType>>(),

        successType = IAppsUpdateConfigResponse.serializer(configSerializer),
        successClass = typeOf<IAppsUpdateConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOf<CommonErrorMessage>(),
    )

    val resetConfiguration: CallDescription<
        IAppsResetConfigRequest<ConfigType>,
        IAppsResetConfigResponse<ConfigType>,
        CommonErrorMessage
    > get() = call(
        name = "resetConfiguration",
        handler = {
            httpUpdate(
                IAppsResetConfigRequest.serializer(configSerializer),
                baseContext,
                "reset",
            )
        },

        requestType = IAppsResetConfigRequest.serializer(configSerializer),
        requestClass = typeOf<IAppsResetConfigRequest<ConfigType>>(),

        successType = IAppsResetConfigResponse.serializer(configSerializer),
        successClass = typeOf<IAppsResetConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOf<CommonErrorMessage>(),
    )
}

@Serializable
class IAppsRetrieveConfigRequest<ConfigType>(val providerId: String)

@Serializable
data class IAppsRetrieveConfigResponse<ConfigType>(
    val etag: String,
    val config: ConfigType
)

@Serializable
data class IAppsUpdateConfigRequest<ConfigType>(
    val providerId: String,
    val config: ConfigType,
    val expectedETag: String? = null,
)

@Serializable
class IAppsUpdateConfigResponse<ConfigType>()

@Serializable
data class IAppsResetConfigRequest<ConfigType>(
    val providerId: String,
    val expectedETag: String? = null,
)

@Serializable
class IAppsResetConfigResponse<ConfigType>()

// Provider API
// ====================================================================================================================

abstract class IntegratedApplicationProviderApi<ConfigType>(
    namespace: String,
    provider: String,
) : CallDescriptionContainer("iapps.$namespace.provider.$provider") {
    val baseContext = "/ucloud/$provider/iapps/$namespace"

    abstract val configType: KType
    abstract val configSerializer: KSerializer<ConfigType>

    val retrieveConfiguration: CallDescription<
        IAppsProviderRetrieveConfigRequest<ConfigType>,
        IAppsProviderRetrieveConfigResponse<ConfigType>,
        CommonErrorMessage
    > get() = call(
        name = "retrieveConfiguration",
        handler = {
            httpUpdate(
                IAppsProviderRetrieveConfigRequest.serializer(configSerializer),
                baseContext,
                "retrieve",
                roles = Roles.SERVICE,
            )
        },

        requestType = IAppsProviderRetrieveConfigRequest.serializer(configSerializer),
        requestClass = typeOf<IAppsProviderRetrieveConfigRequest<ConfigType>>(),

        successType = IAppsProviderRetrieveConfigResponse.serializer(configSerializer),
        successClass = typeOf<IAppsProviderRetrieveConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOf<CommonErrorMessage>(),
    )

    val updateConfiguration: CallDescription<
        IAppsProviderUpdateConfigRequest<ConfigType>,
        IAppsProviderUpdateConfigResponse<ConfigType>,
        CommonErrorMessage
    > get() = call(
        name = "updateConfiguration",
        handler = {
            httpUpdate(
                IAppsProviderUpdateConfigRequest.serializer(configSerializer),
                baseContext,
                "update",
                roles = Roles.SERVICE,
            )
        },

        requestType = IAppsProviderUpdateConfigRequest.serializer(configSerializer),
        requestClass = typeOf<IAppsProviderUpdateConfigRequest<ConfigType>>(),

        successType = IAppsProviderUpdateConfigResponse.serializer(configSerializer),
        successClass = typeOf<IAppsProviderUpdateConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOf<CommonErrorMessage>(),
    )

    val resetConfiguration: CallDescription<
        IAppsProviderResetConfigRequest<ConfigType>,
        IAppsProviderResetConfigResponse<ConfigType>,
        CommonErrorMessage
    > get() = call(
        name = "resetConfiguration",
        handler = {
            httpUpdate(
                IAppsProviderResetConfigRequest.serializer(configSerializer),
                baseContext,
                "reset",
                roles = Roles.SERVICE,
            )
        },

        requestType = IAppsProviderResetConfigRequest.serializer(configSerializer),
        requestClass = typeOf<IAppsProviderResetConfigRequest<ConfigType>>(),

        successType = IAppsProviderResetConfigResponse.serializer(configSerializer),
        successClass = typeOf<IAppsProviderResetConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOf<CommonErrorMessage>(),
    )
}

@Serializable
class IAppsProviderRetrieveConfigRequest<ConfigType>(
    val principal: ResourceOwner,
)

typealias IAppsProviderRetrieveConfigResponse<ConfigType> = IAppsRetrieveConfigResponse<ConfigType>

@Serializable
data class IAppsProviderUpdateConfigRequest<ConfigType>(
    val principal: ResourceOwner,
    val config: ConfigType,
    val expectedETag: String? = null,
)

typealias IAppsProviderUpdateConfigResponse<ConfigType> = IAppsUpdateConfigResponse<ConfigType>

@Serializable
data class IAppsProviderResetConfigRequest<ConfigType>(
    val principal: ResourceOwner,
    val expectedETag: String? = null,
)

typealias IAppsProviderResetConfigResponse<ConfigType> = IAppsResetConfigResponse<ConfigType>


