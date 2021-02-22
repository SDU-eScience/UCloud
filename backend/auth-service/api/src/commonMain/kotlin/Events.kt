package dk.sdu.cloud.auth.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class UserEvent {
    abstract val userId: String

    @Serializable
    @SerialName("created")
    data class Created(override val userId: String, val userCreated: Principal) : UserEvent()

    @Serializable
    @SerialName("updated")
    data class Updated(override val userId: String, val updatedUser: Principal) : UserEvent()
}

