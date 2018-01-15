package dk.sdu.cloud.storage.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = Entity.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = User::class, name = "user"),
        JsonSubTypes.Type(value = Group::class, name = "group"))
sealed class Entity(val name: String, val displayName: String, val zone: String) {
    companion object {
        const val TYPE_PROPERTY = "type"
    }
}

class User(name: String, displayName: String, zone: String) : Entity(name, displayName, zone) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int = javaClass.hashCode()
}

class Group(name: String, displayName: String, zone: String) : Entity(name, displayName, zone) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

