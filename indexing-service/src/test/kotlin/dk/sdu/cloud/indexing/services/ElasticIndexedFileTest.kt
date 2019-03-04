package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.Timestamps
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElasticIndexedFileTest {

    @Test
    fun `Simple create ElasticIndexedFile test`() {
        val elasticfile = ElasticIndexedFile(
            "ID",
            "path",
            "fileName",
            "Owner",
            2,
            FileType.FILE,
            123456,
            Timestamps(123456789, 12345, 1234567),
            FileChecksum("sha", "checksum"),
            false,
            null,
            null,
            SensitivityLevel.CONFIDENTIAL,
            setOf("P")
        )

        val materializedElasticFile = elasticfile.toMaterializedFile()

        assertEquals("ID", materializedElasticFile.fileId)
        assertEquals("path", materializedElasticFile.path)
        assertEquals("Owner", materializedElasticFile.ownerName)
        assertEquals(FileType.FILE, materializedElasticFile.fileType)
        assertEquals(123456, materializedElasticFile.size)
        assertFalse(materializedElasticFile.link)
        assertEquals("P", materializedElasticFile.annotations.first())
        assertEquals(SensitivityLevel.CONFIDENTIAL, materializedElasticFile.ownSensitivityLevel)

        assertEquals("id", ElasticIndexedFile.ID_FIELD)
        assertEquals("path", ElasticIndexedFile.PATH_FIELD)
        assertEquals("fileName", ElasticIndexedFile.FILE_NAME_FIELD)
        assertEquals("fileName.keyword", ElasticIndexedFile.FILE_NAME_KEYWORD)
        assertEquals("fileName.extension", ElasticIndexedFile.FILE_NAME_EXTENSION)
        assertEquals("owner", ElasticIndexedFile.OWNER_FIELD)
        assertEquals("fileDepth", ElasticIndexedFile.FILE_DEPTH_FIELD)
        assertEquals("fileType", ElasticIndexedFile.FILE_TYPE_FIELD)
        assertEquals("size", ElasticIndexedFile.SIZE_FIELD)
        assertEquals("fileTimestamps", ElasticIndexedFile.FILE_TIMESTAMPS_FIELD)
        assertEquals("fileTimestamps.created", ElasticIndexedFile.TIMESTAMP_CREATED_FIELD)
        assertEquals("fileTimestamps.modified", ElasticIndexedFile.TIMESTAMP_MODIFIED_FIELD)
        assertEquals("fileTimestamps.accessed", ElasticIndexedFile.TIMESTAMP_ACCESSED_FIELD)
        assertEquals("checksum", ElasticIndexedFile.CHECKSUM_FIELD)
        assertEquals("fileIsLink", ElasticIndexedFile.FILE_IS_LINK_FIELD)
        assertEquals("linkTarget", ElasticIndexedFile.LINK_TARGET_FIELD)
        assertEquals("linkTargetId", ElasticIndexedFile.LINK_TARGET_ID_FIELD)
        assertEquals("sensitivity", ElasticIndexedFile.SENSITIVITY_FIELD)
        assertEquals("annotations", ElasticIndexedFile.ANNOTATIONS_FIELD)

        elasticfile.hashCode()
        elasticfile.toString()
        assertTrue(elasticfile.equals(elasticfile))
        assertFalse(elasticfile.equals(elasticfile.copy(annotations = setOf("anno"))))

    }

}
