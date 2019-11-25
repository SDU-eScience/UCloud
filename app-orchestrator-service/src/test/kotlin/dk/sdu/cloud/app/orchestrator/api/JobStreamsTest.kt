package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.app.orchestrator.utils.storageFile
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.path
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JobStreamsTest {

    @Test
    fun `simple Validated File for upload class test`() {
        val validFile = ValidatedFileForUpload(
            "id",
            storageFile,
            "dest",
            "destPath",
            "srcPath",
            FileForUploadArchiveType.ZIP,
            true
        )

        assertEquals("id", validFile.id)
        assertEquals("path/to", validFile.stat.path)
        assertEquals("dest", validFile.destinationFileName)
        assertEquals("destPath", validFile.destinationPath)
        assertEquals("srcPath", validFile.sourcePath)
        assertEquals(FileForUploadArchiveType.ZIP, validFile.needsExtractionOfType)
        assertTrue(validFile.readOnly)

    }

    @Test
    fun `simple Validated File for upload class test - default`() {
        val validFile = ValidatedFileForUpload(
            "id",
            storageFile,
            "dest",
            "destPath",
            "srcPath",
            FileForUploadArchiveType.ZIP
        )

        assertEquals("id", validFile.id)
        assertEquals("path/to", validFile.stat.path)
        assertEquals("dest", validFile.destinationFileName)
        assertEquals("destPath", validFile.destinationPath)
        assertEquals("srcPath", validFile.sourcePath)
        assertEquals(FileForUploadArchiveType.ZIP, validFile.needsExtractionOfType)
        assertFalse(validFile.readOnly)
    }
}
