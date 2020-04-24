package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.ownSensitivityLevel
import dk.sdu.cloud.file.api.ownerName
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.api.size
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElasticIndexedFileTest {
    @Test
    fun `Simple create ElasticIndexedFile test`() {
        val elasticfile = ElasticIndexedFile(
            "/home/Owner/path",
            123456L,
            FileType.FILE,
            "123456789"
        )

        val materializedElasticFile = elasticfile.toMaterializedFile()

        assertEquals("/home/Owner/path", materializedElasticFile.path)
        assertEquals("Owner", materializedElasticFile.ownerName)
        assertEquals(FileType.FILE, materializedElasticFile.fileType)
        assertEquals(123456, materializedElasticFile.size)

        assertEquals("path", ElasticIndexedFile.PATH_FIELD)
        assertEquals("fileName", ElasticIndexedFile.FILE_NAME_FIELD)
        assertEquals("fileName.keyword", ElasticIndexedFile.FILE_NAME_KEYWORD)
        assertEquals("fileName.extension", ElasticIndexedFile.FILE_NAME_EXTENSION)
        assertEquals("fileDepth", ElasticIndexedFile.FILE_DEPTH_FIELD)
        assertEquals("fileType", ElasticIndexedFile.FILE_TYPE_FIELD)
        assertEquals("size", ElasticIndexedFile.SIZE_FIELD)

        elasticfile.hashCode()
        elasticfile.toString()
        assertTrue(elasticfile.equals(elasticfile))
    }
}
