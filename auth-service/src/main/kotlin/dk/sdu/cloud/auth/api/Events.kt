package dk.sdu.cloud.auth.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.KafkaRequest

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = KafkaRequest.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = UserEvent.Created::class, name = "created"),
        JsonSubTypes.Type(value = UserEvent.Updated::class, name = "updated"))
sealed class UserEvent {
    abstract val userId: String
    @get:JsonIgnore val key: String get() = userId

    data class Created(override val userId: String, val userCreated: Principal) : UserEvent()
    data class Updated(override val userId: String, val updatedUser: Principal) : UserEvent()
}

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = KafkaRequest.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = RefreshTokenEvent.Created::class, name = "created"),
        JsonSubTypes.Type(value = RefreshTokenEvent.Invoked::class, name = "invoked"),
        JsonSubTypes.Type(value = RefreshTokenEvent.Invalidated::class, name = "invalidated"))
sealed class RefreshTokenEvent {
    abstract val token: String
    @get:JsonIgnore val key: String get() = token

    data class Created(override val token: String, val associatedUser: String) : RefreshTokenEvent()
    data class Invoked(override val token: String, val generatedAccessToken: String) : RefreshTokenEvent()
    data class Invalidated(override val token: String) : RefreshTokenEvent()
}