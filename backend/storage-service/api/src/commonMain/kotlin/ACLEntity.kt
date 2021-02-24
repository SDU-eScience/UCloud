package dk.sdu.cloud.file.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ACLEntity {
    @Serializable
    @SerialName("user")
    data class User(val username: String) : ACLEntity() {
        init {
            require(!username.contains("\n"))
            require(username.length in 1..2048)
        }
    }

    @Serializable
    @SerialName("project_group")
    data class ProjectAndGroup(val projectId: String, val group: String) : ACLEntity() {
        init {
            require(!projectId.contains("\n"))
            require(!group.contains("\n"))
            require(projectId.length in 1..2048)
            require(group.length in 0..2048)
        }
    }
}
