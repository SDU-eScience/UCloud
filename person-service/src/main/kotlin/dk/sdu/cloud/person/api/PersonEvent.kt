package dk.sdu.cloud.person.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.KafkaRequest

/**
 * Contains the events that can occur for a single person.
 *
 * Events are emitted into Kafka. All state in the service is calculated from the events emitted. As a result, if any
 * database table should be maintained it should be maintained _strictly_ from these events.
 *
 * The streams are described in [dk.sdu.cloud.project.api.internal.ProjectStreams].
 *
 * Events are not used in requests. Requests use the command objects, for simple command objects, where we have a
 * near one-to-one mapping between events and commands we can wrap an event in a [dk.sdu.cloud.service.KafkaRequest].
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = KafkaRequest.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = PersonEvent.Created::class, name = "created"),
        JsonSubTypes.Type(value = PersonEvent.Updated::class, name = "updated"),
        JsonSubTypes.Type(value = PersonEvent.Activated::class, name = "activated"),
        JsonSubTypes.Type(value = PersonEvent.Deactivated::class, name = "deactivated"),
        JsonSubTypes.Type(value = PersonEvent.Deleted::class, name = "deleted"),
        JsonSubTypes.Type(value = PersonEvent.AddMember::class, name = "add_member"),
        JsonSubTypes.Type(value = PersonEvent.RemoveMember::class, name = "remove_member"))
sealed class PersonEvent {
    abstract val id: Long

    // TODO This design will only work if the Kafka topics have retention period that does _not_ expire.
    //
    // We cannot simply use compaction since that would delete information required to rebuilt state. In order to
    // use compaction we would require every event to contain enough state that we could use it in isolation (i.e.
    // without considering previous events on same key).

    data class Created(override val id: Long, val name: String) : PersonEvent()
    data class Updated(override val id: Long) : PersonEvent()

    data class Activated(override val id: Long) : PersonEvent()
    data class Deactivated(override val id: Long) : PersonEvent()
    data class Deleted(override val id: Long) : PersonEvent()

    data class AddMember(override val id: Long, val member: String) : PersonEvent()
    data class RemoveMember(override val id: Long, val member: String) : PersonEvent()
}