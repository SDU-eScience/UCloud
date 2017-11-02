package org.esciencecloud.storage.server

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.apache.kafka.common.serialization.Serdes
import org.esciencecloud.kafka.JsonSerde.jsonSerde

// Shared interface stuff. Should be published as a separate artifact
// These artifacts should be shared with others, such that they may be used for types

// TODO This should also include certain types of the storage interfaces, but no longer the storage interface themselves
// this will have to be changed later.

data class Request<out EventType>(val header: RequestHeader, val event: EventType) {
    companion object {
        const val TYPE_PROPERTY = "type"
    }
}

data class RequestHeader(
        val uuid: String,
        val performedFor: ProxyClient
)

// This will change over time. Should use a token instead of a straight password. We won't need the username at that
// point, since we could retrieve this from the auth service instead.
data class ProxyClient(val username: String, val password: String)

class Response<out InputType : Any>(
        val successful: Boolean,
        val errorMessage: String?,
        val input: Request<InputType>
)

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = Request.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = UserEvent.Create::class, name = "create"),
        JsonSubTypes.Type(value = UserEvent.Modify::class, name = "modify"),
        JsonSubTypes.Type(value = UserEvent.Delete::class, name = "delete"))
sealed class UserEvent {
    data class Create(
            val username: String,
            val password: String?,
            val userType: UserType // <-- Shared type that lives inside storage interface
    ) : UserEvent()

    data class Modify(
            val currentUsername: String,
            val newPassword: String?,
            val newUserType: UserType?
    ) : UserEvent()

    data class Delete(val username: String) : UserEvent()
}

object UserProcessor {
    // TODO Having auth be validated in the processor probably makes it quite a bit harder to use event-sourcing.
    // The tokens would have validate during a replay. Food for thought...
    // Maybe not, we might be able to just use the response topic? We really do need to look into this though.
    //
    // It would also make a lot of sense if these events were sent on the same topic, but with different payloads.
    // This also appears to be how most people describe event-sourcing. Using the primary key (i.e. user) would also
    // significantly help the ordering of things.
    val UserEvents = RequestResponseStream<String, UserEvent>("users", Serdes.String(), jsonSerde(), jsonSerde())
}
