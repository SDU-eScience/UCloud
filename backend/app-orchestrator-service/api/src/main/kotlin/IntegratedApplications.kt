package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceOwner
import kotlinx.serialization.*
import kotlin.reflect.KType

// End-user API
// ====================================================================================================================

abstract class IntegratedApplicationApi<ConfigType>(
    namespace: String
) : CallDescriptionContainer("iapps.$namespace") {
    val baseContext = "/api/iapps/$namespace"

    abstract val configType: KType?
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
                typeOfIfPossible<IAppsRetrieveConfigRequest<ConfigType>>(),
                baseContext,
            )
        },

        requestType = IAppsRetrieveConfigRequest.serializer(configSerializer),
        requestClass = typeOfIfPossible<IAppsRetrieveConfigRequest<ConfigType>>(),

        successType = IAppsRetrieveConfigResponse.serializer(configSerializer),
        successClass = typeOfIfPossible<IAppsRetrieveConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOfIfPossible<CommonErrorMessage>(),
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
        requestClass = typeOfIfPossible<IAppsUpdateConfigRequest<ConfigType>>(),

        successType = IAppsUpdateConfigResponse.serializer(configSerializer),
        successClass = typeOfIfPossible<IAppsUpdateConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOfIfPossible<CommonErrorMessage>(),
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
        requestClass = typeOfIfPossible<IAppsResetConfigRequest<ConfigType>>(),

        successType = IAppsResetConfigResponse.serializer(configSerializer),
        successClass = typeOfIfPossible<IAppsResetConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOfIfPossible<CommonErrorMessage>(),
    )

    val restart: CallDescription<
        IAppsRestartRequest<ConfigType>,
        IAppsRestartResponse<ConfigType>,
        CommonErrorMessage
    > get() = call(
        name = "restart",
        handler = {
            httpUpdate(
                IAppsRestartRequest.serializer(configSerializer),
                baseContext,
                "restart",
            )
        },

        requestType = IAppsRestartRequest.serializer(configSerializer),
        requestClass = typeOfIfPossible<IAppsRestartRequest<ConfigType>>(),

        successType = IAppsRestartResponse.serializer(configSerializer),
        successClass = typeOfIfPossible<IAppsRestartResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOfIfPossible<CommonErrorMessage>(),
    )
}

@Serializable
class IAppsRetrieveConfigRequest<ConfigType>(
    val provider: String,
    val productId: String,
)

@Serializable
data class IAppsRetrieveConfigResponse<ConfigType>(
    val etag: String,
    val config: ConfigType
)

@Serializable
data class IAppsUpdateConfigRequest<ConfigType>(
    val provider: String,
    val productId: String,
    val config: ConfigType,
    val expectedETag: String? = null,
)

@Serializable
class IAppsUpdateConfigResponse<ConfigType>()

@Serializable
data class IAppsResetConfigRequest<ConfigType>(
    val provider: String,
    val productId: String,
    val expectedETag: String? = null,
)

@Serializable
class IAppsResetConfigResponse<ConfigType>()

@Serializable
data class IAppsRestartRequest<ConfigType>(
    val provider: String,
    val productId: String,
)

@Serializable
class IAppsRestartResponse<ConfigType>()

// Provider API
// ====================================================================================================================

abstract class IntegratedApplicationProviderApi<ConfigType>(
    namespace: String,
    provider: String,
) : CallDescriptionContainer("iapps.$namespace.provider.$provider") {
    val baseContext = "/ucloud/$provider/iapps/$namespace"

    abstract val configType: KType?
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
        requestClass = typeOfIfPossible<IAppsProviderRetrieveConfigRequest<ConfigType>>(),

        successType = IAppsProviderRetrieveConfigResponse.serializer(configSerializer),
        successClass = typeOfIfPossible<IAppsProviderRetrieveConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOfIfPossible<CommonErrorMessage>(),
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
        requestClass = typeOfIfPossible<IAppsProviderUpdateConfigRequest<ConfigType>>(),

        successType = IAppsProviderUpdateConfigResponse.serializer(configSerializer),
        successClass = typeOfIfPossible<IAppsProviderUpdateConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOfIfPossible<CommonErrorMessage>(),
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
        requestClass = typeOfIfPossible<IAppsProviderResetConfigRequest<ConfigType>>(),

        successType = IAppsProviderResetConfigResponse.serializer(configSerializer),
        successClass = typeOfIfPossible<IAppsProviderResetConfigResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOfIfPossible<CommonErrorMessage>(),
    )

    val restart: CallDescription<
        IAppsProviderRestartRequest<ConfigType>,
        IAppsProviderRestartResponse<ConfigType>,
        CommonErrorMessage
    > get() = call(
        name = "restart",
        handler = {
            httpUpdate(
                IAppsProviderRestartRequest.serializer(configSerializer),
                baseContext,
                "restart",
                roles = Roles.SERVICE,
            )
        },

        requestType = IAppsProviderRestartRequest.serializer(configSerializer),
        requestClass = typeOfIfPossible<IAppsProviderRestartRequest<ConfigType>>(),

        successType = IAppsProviderRestartResponse.serializer(configSerializer),
        successClass = typeOfIfPossible<IAppsProviderRestartResponse<ConfigType>>(),

        errorType = CommonErrorMessage.serializer(),
        errorClass = typeOfIfPossible<CommonErrorMessage>(),
    )

}

@Serializable
class IAppsProviderRetrieveConfigRequest<ConfigType>(
    val productId: String,
    val principal: ResourceOwner,
)

typealias IAppsProviderRetrieveConfigResponse<ConfigType> = IAppsRetrieveConfigResponse<ConfigType>

@Serializable
data class IAppsProviderUpdateConfigRequest<ConfigType>(
    val productId: String,
    val principal: ResourceOwner,
    val config: ConfigType,
    val expectedETag: String? = null,
)

typealias IAppsProviderUpdateConfigResponse<ConfigType> = IAppsUpdateConfigResponse<ConfigType>

@Serializable
data class IAppsProviderResetConfigRequest<ConfigType>(
    val productId: String,
    val principal: ResourceOwner,
    val expectedETag: String? = null,
)

typealias IAppsProviderResetConfigResponse<ConfigType> = IAppsResetConfigResponse<ConfigType>

@Serializable
data class IAppsProviderRestartRequest<ConfigType>(
    val productId: String,
    val principal: ResourceOwner,
)

typealias IAppsProviderRestartResponse<ConfigType> = IAppsRestartResponse<ConfigType>


