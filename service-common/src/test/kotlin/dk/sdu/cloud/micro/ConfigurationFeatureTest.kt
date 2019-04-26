package dk.sdu.cloud.micro

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals

private val jsonFile by lazy {
    Files.createTempFile("config", ".json").toFile().also {
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

    private val jsonNode = defaultMapper.readTree(jsonFile)

    @Test
    fun `requestChunk test`() {
        val serverConfig = ServerConfiguration(defaultMapper, jsonNode)

        val returnedValue = serverConfig.requestChunk<String>("hibernate")
        assertEquals("hello", returnedValue)
    }

    @Test (expected = ServerConfigurationException.MissingNode::class)
    fun `requestChunk - missing node`() {
        val serverConfig = ServerConfiguration(defaultMapper, jsonNode)
        serverConfig.requestChunk<String>("node")

        val micro = initializeMicro()
        val config = micro.configuration.requestChunkAtOrNull("node") ?: "default"
        assertEquals("default", config)
    }

    @Test
    fun `requestChunkAt test`() {
        val serverConfig = ServerConfiguration(defaultMapper, jsonNode)
        val returnedValue = serverConfig.requestChunkAt<String>("hibernate")
        assertEquals("hello", returnedValue)
    }

    @Test (expected = ServerConfigurationException.MissingNode::class)
    fun `requestChunkAt - missing node`() {
        val serverConfig = ServerConfiguration(defaultMapper, jsonNode)

        serverConfig.requestChunkAt<String>("noPath")
        val micro = initializeMicro()
        val config = micro.configuration.requestChunkAtOrNull("noPath") ?: "default"
        assertEquals("default", config)
    }

    @Test
    fun `configuration feature test - config-dir`() {
        val micro = Micro()
        val configFeature = ConfigurationFeature()
        configFeature.init(micro, mockk(), listOf("--config-dir", jsonFile.parent))
    }

    @Test
    fun `configuration feature test - config-dir - dangling`() {
        val micro = Micro()
        val configFeature = ConfigurationFeature()
        configFeature.init(micro, mockk(), listOf("--config-dir"))
    }

    @Test
    fun `configuration feature test - dangling config`() {
        val micro = Micro()
        val configFeature = ConfigurationFeature()
        configFeature.init(micro, mockk(), listOf("--config"))
    }

    @Test
    fun `configuration feature test - no args`() {
        val micro = initializeMicro()
        val configFeature = ConfigurationFeature()
        configFeature.init(micro, mockk(), emptyList())
    }

    @Test
    fun `manual inject test`() {
        val configFeature = ConfigurationFeature()
        val serverConfig = ServerConfiguration(defaultMapper, jsonNode)
        configFeature.manuallyInjectNode(serverConfig, jsonNode)
    }

    @Test
    fun `Inject test - config does not exists`() {
        val configFeature = ConfigurationFeature()
        val serverConfig = ServerConfiguration(defaultMapper, jsonNode)
        configFeature.injectFile(serverConfig, File("/not/a/path"))
    }
}
