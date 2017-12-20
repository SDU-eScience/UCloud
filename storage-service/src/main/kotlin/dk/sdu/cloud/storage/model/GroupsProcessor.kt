package dk.sdu.cloud.storage.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.JsonSerde.jsonSerde
import org.apache.kafka.common.serialization.Serdes

object GroupsProcessor {
    val Groups = RequestResponseStream<String, GroupEvent>("groups", Serdes.String(), jsonSerde(), jsonSerde())
}

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = Request.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = GroupEvent.Create::class, name = "create"),
        JsonSubTypes.Type(value = GroupEvent.AddMember::class, name = "addMember"),
        JsonSubTypes.Type(value = GroupEvent.RemoveMember::class, name = "removeMember"),
        JsonSubTypes.Type(value = GroupEvent.Delete::class, name = "delete"))
sealed class GroupEvent {
    data class Create(val groupName: String) : GroupEvent()
    data class AddMember(val groupName: String, val username: String) : GroupEvent()
    data class RemoveMember(val groupName: String, val username: String) : GroupEvent()
    data class Delete(val groupName: String, val force: Boolean) : GroupEvent()
}
