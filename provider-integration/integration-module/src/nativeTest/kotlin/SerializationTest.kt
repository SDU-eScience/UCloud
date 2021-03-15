/*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkRequestSerializer
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.defaultMapper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import kotlin.test.*

@Serializable
data class Item(val fie: String)

class SerializationTest {
    @Test
    fun `test serialization of multiple`() {
        val item = bulkRequestOf(Item("Fie"), Item("er"), Item("en"), Item("hund"))
        val encoded = defaultMapper.encodeToString(BulkRequestSerializer(Item.serializer()), item)
        println(encoded)
        val decoded = defaultMapper.decodeFromString(BulkRequestSerializer(Item.serializer()), encoded)
        assertEquals(item, decoded)
    }

    @Test
    fun `test serialization of single`() {
        val item = bulkRequestOf(Item("Fie"))
        val encoded = defaultMapper.encodeToString(BulkRequestSerializer(Item.serializer()), item)
        println(encoded)
        val decoded = defaultMapper.decodeFromString(BulkRequestSerializer(Item.serializer()), encoded)
        assertEquals(item, decoded)
    }
}


 */