package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.SensitivityLevel
import dk.sdu.cloud.storage.services.CloudToCephFsDao
import dk.sdu.cloud.storage.services.FileSystemService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileSystemServiceTest {
    private val service = FileSystemService(CloudToCephFsDao(), true)

    @Test
    fun testOutputParsing() {
        val where = File("/f/shared")

        val output = """
           D,509,root,root,4096,1523862649,1523862649,1523862650,3,user1,14,user2,2,user3,6,CONFIDENTIAL,.
           D,493,root,root,4096,1523862224,1523862224,1523862237,0,CONFIDENTIAL,..
           F,420,root,root,0,1523862649,1523862649,1523862649,0,CONFIDENTIAL,qwe
        """.trimIndent()

        val result = service.parseDirListingOutput(where, output)
        assertEquals(3, result.size)

        run {
            val firstFile = result[0]
            assertEquals(FileType.DIRECTORY, firstFile.type)
            assertEquals("root", firstFile.ownerName)
            assertEquals(4096, firstFile.size)
            assertEquals(1523862649000, firstFile.createdAt)
            assertEquals(1523862649000, firstFile.modifiedAt)
            val firstAcl = firstFile.acl!!
            assertEquals(3, firstAcl.size)

            assertEquals("user1", firstAcl[0].entity)
            assertEquals(false, firstAcl[0].isGroup)
            assertTrue(AccessRight.READ in firstAcl[0].rights)
            assertTrue(AccessRight.WRITE in firstAcl[0].rights)
            assertTrue(AccessRight.EXECUTE in firstAcl[0].rights)

            assertEquals("user2", firstAcl[1].entity)
            assertEquals(false, firstAcl[1].isGroup)
            assertTrue(AccessRight.READ in firstAcl[1].rights)

            assertEquals("user3", firstAcl[2].entity)
            assertEquals(false, firstAcl[2].isGroup)
            assertTrue(AccessRight.READ in firstAcl[2].rights)
            assertTrue(AccessRight.WRITE in firstAcl[2].rights)

            assertEquals(SensitivityLevel.CONFIDENTIAL, firstFile.sensitivityLevel)
            assertEquals("/f/shared", firstFile.path)
        }

        run {
            val file = result[1]
            assertEquals(FileType.DIRECTORY, file.type)
            assertEquals("root", file.ownerName)
            assertEquals(4096, file.size)
            assertEquals(1523862224000, file.createdAt)
            assertEquals(1523862224000, file.modifiedAt)
            val acl = file.acl!!
            assertEquals(0, acl.size)

            assertEquals(SensitivityLevel.CONFIDENTIAL, file.sensitivityLevel)
            assertEquals("/f", file.path)
        }

        run {
            val file = result[2]
            assertEquals(FileType.FILE, file.type)
            assertEquals("root", file.ownerName)
            assertEquals(0, file.size)
            assertEquals(1523862649000, file.createdAt)
            assertEquals(1523862649000, file.modifiedAt)
            val acl = file.acl!!
            assertEquals(0, acl.size)

            assertEquals(SensitivityLevel.CONFIDENTIAL, file.sensitivityLevel)
            assertEquals("/f/shared/qwe", file.path)
        }
    }
}