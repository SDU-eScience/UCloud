package dk.sdu.cloud.config

import kotlinx.serialization.*

@Serializable
@SerialName("Posix")
data class PosixFilesConfiguration(
    override val matches: String,
) : ConfigSchema.Plugins.Files()

@Serializable
@SerialName("Posix")
data class PosixFileCollectionsConfiguration(
    override val matches: String,
    val simpleHomeMapper: List<HomeMapper> = emptyList(),
    val extensions: Extensions = Extensions(),
    val accounting: String?,
) : ConfigSchema.Plugins.FileCollections() {
    @Serializable
    data class HomeMapper(
        val title: String,
        val prefix: String,
    )

    @Serializable
    data class Extensions(
        val additionalCollections: String? = null,
    )
}

