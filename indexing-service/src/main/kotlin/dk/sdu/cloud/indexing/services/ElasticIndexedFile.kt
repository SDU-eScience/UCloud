package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.storage.api.*

data class ElasticIndexedFile(
    val id: String,
    val path: String,
    val fileName: String,
    val owner: String,

    /**
     * Depth in the file hierarchy
     *
     * Example: /a/b/c will have a depth of 3 and /a/b/c/d will have a depth of 4 and / will have a depth of 0
     */
    val fileDepth: Int,

    val fileType: FileType,

    val size: Long,
    val fileTimestamps: Timestamps,
    val checksum: FileChecksum,

    val fileIsLink: Boolean,
    val linkTarget: String?,
    val linkTargetId: String?,

    val sensitivity: SensitivityLevel,

    val annotations: Set<String>
) {
    fun toMaterializedFile(): EventMaterializedStorageFile = EventMaterializedStorageFile(
        id,
        path,
        owner,
        fileType,
        fileTimestamps,
        size,
        checksum,
        fileIsLink,
        linkTarget,
        linkTargetId,
        annotations,
        sensitivity
    )

    @Suppress("unused")
    companion object {
        // Refactoring safe without most of the performance penalty
        val ID_FIELD = ElasticIndexedFile::id.name
        val PATH_FIELD = ElasticIndexedFile::path.name
        val FILE_NAME_FIELD = ElasticIndexedFile::fileName.name
        val FILE_NAME_KEYWORD = ElasticIndexedFile::fileName.name + ".keyword"
        val OWNER_FIELD = ElasticIndexedFile::owner.name
        val FILE_DEPTH_FIELD = ElasticIndexedFile::fileDepth.name

        val FILE_TYPE_FIELD = ElasticIndexedFile::fileType.name
        val SIZE_FIELD = ElasticIndexedFile::size.name
        val FILE_TIMESTAMPS_FIELD = ElasticIndexedFile::fileTimestamps.name

        val CHECKSUM_FIELD = ElasticIndexedFile::checksum.name
        val FILE_IS_LINK_FIELD = ElasticIndexedFile::fileIsLink.name
        val LINK_TARGET_FIELD = ElasticIndexedFile::linkTarget.name

        val LINK_TARGET_ID_FIELD = ElasticIndexedFile::linkTargetId.name

        val SENSITIVITY_FIELD = ElasticIndexedFile::sensitivity.name
        val ANNOTATIONS_FIELD = ElasticIndexedFile::annotations.name

    }
}