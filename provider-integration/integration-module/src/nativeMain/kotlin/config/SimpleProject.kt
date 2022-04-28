package dk.sdu.cloud.config

import kotlinx.serialization.*

@Serializable
@SerialName("Simple")
data class SimpleProjectConfiguration(
    val unixGroupNamespace: Int,
    val extensions: Extensions,
) : ConfigSchema.Plugins.Projects() {
    @Serializable
    data class Extensions(
        val all: String? = null,

        val projectRenamed: String? = null,

        val membersAddedToProject: String? = null,
        val membersRemovedFromProject: String? = null,

        val membersAddedToGroup: String? = null,
        val membersRemovedFromGroup: String? = null,

        val projectArchived: String? = null,
        val projectUnarchived: String? = null,

        val roleChanged: String? = null,

        val groupCreated: String? = null,
        val groupRenamed: String? = null,
        val groupDeleted: String? = null,
    )
}

