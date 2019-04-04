package dk.sdu.cloud.micro

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals

private val node by lazy {
    Files.createTempFile("config", ".yml").toFile().also {
        // language=json
        it.writeText(
            """
           {
            "hibernate":"hello"
           }
            """.trimIndent()
        )
    }
}

class ConfigurationFeatureTest{

    private val realNode = defaultMapper.readTree(node)

    @Test
    fun `requestChunk test`() {
        val serverConfig = ServerConfiguration(defaultMapper, realNode)

        val returnedValue = serverConfig.requestChunk<String>("hibernate")
        assertEquals("hello", returnedValue)
    }

    @Test (expected = ServerConfigurationException.MissingNode::class)
    fun `requestChunk - missing node`() {
        val serverConfig = ServerConfiguration(defaultMapper, realNode)
        serverConfig.requestChunk<String>("node")

        val micro = initializeMicro()
        val config = micro.configuration.requestChunkAtOrNull("node") ?: "default"
        assertEquals("default", config)
    }

    @Test
    fun `requestChunkAt test`() {
        val serverConfig = ServerConfiguration(defaultMapper, realNode)
        val returnedValue = serverConfig.requestChunkAt<String>("hibernate")
        assertEquals("hello", returnedValue)
    }

    @Test (expected = ServerConfigurationException.MissingNode::class)
    fun `requestChunkAt - missing node`() {
        val serverConfig = ServerConfiguration(defaultMapper, realNode)

        serverConfig.requestChunkAt<String>("noPath")
        val micro = initializeMicro()
        val config = micro.configuration.requestChunkAtOrNull("noPath") ?: "default"
        assertEquals("default", config)
    }
}
