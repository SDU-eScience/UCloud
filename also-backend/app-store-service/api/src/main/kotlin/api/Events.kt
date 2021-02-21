package dk.sdu.cloud.app.store.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.TYPE_PROPERTY
import java.util.*

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AppEvent.Deleted::class, name = "deleted")
)
sealed class AppEvent {
    abstract val appName: String
    abstract val appVersion: String
    @get:JsonIgnore
    val key: String
        get() = UUID.randomUUID().toString()

    data class Deleted(
        override val appName: String,
        override val appVersion: String
    ) : AppEvent()
}
