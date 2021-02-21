package dk.sdu.cloud.auth.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.TYPE_PROPERTY

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = UserEvent.Created::class, name = "created"),
    JsonSubTypes.Type(value = UserEvent.Updated::class, name = "updated")
)
sealed class UserEvent {
    abstract val userId: String
    @get:JsonIgnore
    val key: String
        get() = userId

    data class Created(override val userId: String, val userCreated: Principal) : UserEvent()
    data class Updated(override val userId: String, val updatedUser: Principal) : UserEvent()
}
