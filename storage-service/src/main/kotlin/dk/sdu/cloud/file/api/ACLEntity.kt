package dk.sdu.cloud.file.api

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode

@JsonDeserialize(using = ACLEntityDeserializer::class)
@JsonSerialize(using = ACLEntitySerializer::class)
sealed class ACLEntity {
    data class User(val username: String) : ACLEntity() {
        init {
            require(!username.contains("\n"))
            require(username.length in 1..2048)
        }
    }

    data class ProjectAndGroup(val projectId: String, val group: String) : ACLEntity() {
        init {
            require(!projectId.contains("\n"))
            require(!group.contains("\n"))
            require(projectId.length in 1..2048)
            require(group.length in 0..2048)
        }
    }
}

class ACLEntitySerializer : StdSerializer<ACLEntity>(ACLEntity::class.java) {
    override fun serialize(value: ACLEntity?, gen: JsonGenerator, provider: SerializerProvider?) {
        when (value) {
            null -> {
                gen.writeNull()
            }

            is ACLEntity.User -> {
                gen.writeString(value.username)
            }

            is ACLEntity.ProjectAndGroup -> {
                gen.writeStartObject()

                gen.writeFieldName("projectId")
                gen.writeString(value.projectId)

                gen.writeFieldName("group")
                gen.writeString(value.group)

                gen.writeEndObject()
            }
        }
    }
}

class ACLEntityDeserializer : StdDeserializer<ACLEntity>(ACLEntity::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): ACLEntity? {
        return when (p.currentToken()) {
            JsonToken.VALUE_NULL -> null

            JsonToken.VALUE_STRING -> {
                ACLEntity.User(p.valueAsString)
            }

            JsonToken.START_OBJECT -> {
                val tree = p.codec.readTree<JsonNode>(p)
                val projectId = tree["projectId"].takeIf { !it.isNull && it.isTextual }?.textValue()
                val group = tree["group"].takeIf { !it.isNull && it.isTextual }?.textValue()
                if (projectId == null || group == null) {
                    throw RPCException("Bad ACL entity", HttpStatusCode.BadRequest)
                }

                ACLEntity.ProjectAndGroup(projectId, group)
            }

            else -> {
                throw RPCException("Bad ACL entity", HttpStatusCode.BadRequest)
            }
        }
    }
}
