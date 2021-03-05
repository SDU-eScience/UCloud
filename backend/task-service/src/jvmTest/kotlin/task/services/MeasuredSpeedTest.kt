package dk.sdu.cloud.task.services

import com.fasterxml.jackson.databind.node.ObjectNode
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.task.api.MeasuredSpeedInteger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Ignore
import kotlin.test.Test

class MeasuredSpeedTest {
    @Ignore
    @Test
    fun `test json serialization`() {
        val measurer = MeasuredSpeedInteger("Title", "Foo")
        measurer.start()
        Thread.sleep(100)
        measurer.increment(1)
        val writeValueAsString = defaultMapper.encodeToString(measurer)
        println(writeValueAsString)
        val tree = defaultMapper.decodeFromString(writeValueAsString) as ObjectNode
        val fields = tree.fieldNames().asSequence().toList()
        assertThatInstance(fields) { it.contains("speed") }
        assertThatInstance(fields) { it.contains("unit") }
        assertThatInstance(fields) { it.contains("title") }
        assertThatInstance(fields) { it.contains("asText") }
        assertThatInstance(fields) { it.size == 4 }
    }

    @Ignore
    @Test
    fun `test json serialization - custom`() {
        val measurer = MeasuredSpeedInteger("Title", "Foo") { "Custom" }
        measurer.start()
        Thread.sleep(100)
        measurer.increment(1)
        val writeValueAsString = defaultMapper.encodeToString(measurer)
        println(writeValueAsString)
        val tree = defaultMapper.decodeFromString(writeValueAsString) as ObjectNode
        val fields = tree.fieldNames().asSequence().toList()
        assertThatInstance(fields) { it.contains("speed") }
        assertThatInstance(fields) { it.contains("unit") }
        assertThatInstance(fields) { it.contains("title") }
        assertThatInstance(fields) { it.contains("asText") }
        assertThatInstance(fields) { it.size == 4 }
        assertThatInstance(tree["asText"]) { it.textValue() == "Custom" }
    }
}
