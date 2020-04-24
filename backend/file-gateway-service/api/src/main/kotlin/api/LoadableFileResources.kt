package dk.sdu.cloud.file.gateway.api

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.treeToValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.StorageFile

val DEFAULT_RESOURCES_TO_LOAD = FileResource.values().joinToString(",") { it.text }

const val FAVORITES_BACKEND = "favorites"
const val STORAGE_BACKEND = "storage"

enum class FileResource(val text: String, val backend: String) {
    FAVORITES("favorited", FAVORITES_BACKEND),

    FILE_TYPE("fileType", STORAGE_BACKEND),
    PATH("path", STORAGE_BACKEND),
    MODIFIED_AT("modifiedAt", STORAGE_BACKEND),
    OWNER_NAME("ownerName", STORAGE_BACKEND),
    SIZE("size", STORAGE_BACKEND),
    ACL("acl", STORAGE_BACKEND),
    SENSITIVITY_LEVEL("sensitivityLevel", STORAGE_BACKEND),
    OWN_SENSITIVITY_LEVEL("ownSensitivityLevel", STORAGE_BACKEND),
    LINK("link", STORAGE_BACKEND),
    CANONICAL_PATH("canonicalPath", STORAGE_BACKEND)
}

internal fun fileResourcesToString(load: Set<FileResource>) =
    load.joinToString(",") { it.text }

interface LoadFileResource {
    val attributes: String?
}

val LoadFileResource.resourcesToLoad: Set<FileResource>
    get() = (attributes ?: DEFAULT_RESOURCES_TO_LOAD).split(",").mapNotNull { param ->
        FileResource.values().find { it.text == param }
    }.toSet().normalize()

@JsonDeserialize(using = StorageFileWithMetadataDeserializer::class)
class StorageFileWithMetadata(
    delegate: StorageFile,

    // custom resources
    val favorited: Boolean?
) : StorageFile by delegate

class StorageFileWithMetadataDeserializer : StdDeserializer<StorageFileWithMetadata>(null as Class<*>?) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): StorageFileWithMetadata {
        val tree = p.codec.readTree<JsonNode>(p)!!
        val delegate = defaultMapper.treeToValue<StorageFile>(tree)
        val favorited = tree["favorited"].takeIf { !it.isNull }?.asBoolean()
        return StorageFileWithMetadata(delegate, favorited)
    }
}

private fun Set<FileResource>.normalize(): Set<FileResource> {
    val result = HashSet(this)
    if (FileResource.FAVORITES in result) {
        result += FileResource.PATH
    }
    return result
}
