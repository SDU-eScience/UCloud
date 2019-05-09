package dk.sdu.cloud.file.api

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import kotlin.test.*

class FileSortByCompatibilityTest {
    private val oldValues = mapOf(
        "TYPE" to FileSortBy.TYPE,
        "PATH" to FileSortBy.PATH,
        "CREATED_AT" to FileSortBy.CREATED_AT,
        "MODIFIED_AT" to FileSortBy.MODIFIED_AT,
        "SIZE" to FileSortBy.SIZE,
        "ACL" to FileSortBy.ACL,
        "SENSITIVITY" to FileSortBy.SENSITIVITY
    )

    private val newValues = mapOf(
        "fileType" to FileSortBy.TYPE,
        "path" to FileSortBy.PATH,
        "createdAt" to FileSortBy.CREATED_AT,
        "modifiedAt" to FileSortBy.MODIFIED_AT,
        "size" to FileSortBy.SIZE,
        "acl" to FileSortBy.ACL,
        "sensitivityLevel" to FileSortBy.SENSITIVITY
    )

    @Test
    fun `test that old values of FileSortBy are accepted`() {
         oldValues.forEach { (text, expectedValue) ->
             assertEquals(expectedValue, defaultMapper.readValue("\"$text\""))
         }
    }

    @Test
    fun `test that new values of FileSortBy are accepted`() {
         newValues.forEach { (text, expectedValue) ->
             assertEquals(expectedValue, defaultMapper.readValue("\"$text\""))
         }
    }

    @Test
    fun `test serialization and deserialization of new values`() {
        newValues.forEach { (text, expectedValue) ->
            val serialized = "\"$text\""
            val deserialized = defaultMapper.readValue<FileSortBy>(serialized)
            assertEquals(expectedValue, deserialized)
            assertEquals(serialized, defaultMapper.writeValueAsString(deserialized))
        }
    }
}
