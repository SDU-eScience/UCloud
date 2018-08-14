package dk.sdu.cloud.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.client.ServiceDescription
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals

class ConfigurationFeatureTest {
    private val description = object : ServiceDescription {
        override val name: String = "configuration-feature-test"
        override val version: String = "1.0.0"
    }

    @Test
    fun `test simple config from one file`() {
        val configFile = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "testing": [1, 2, 3, 4]
                }
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", configFile.absolutePath))
        micro.install(ConfigurationFeature)

        val configChunk = micro.configuration.requestChunkAt<List<Int>>("testing")
        assertEquals(listOf(1, 2, 3, 4), configChunk)
    }

    @Test
    fun `test simple config from one file - with dangling`() {
        val configFile = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "testing": [1, 2, 3, 4]
                }
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", configFile.absolutePath, "--config"))
        micro.install(ConfigurationFeature)

        val configChunk = micro.configuration.requestChunkAt<List<Int>>("testing")
        assertEquals(listOf(1, 2, 3, 4), configChunk)
    }

    @Test
    fun `test simple config from one file, nested`() {
        val configFile = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "nested": {
                    "testing": [1, 2, 3, 4]
                  }
                }
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", configFile.absolutePath))
        micro.install(ConfigurationFeature)

        val configChunk = micro.configuration.requestChunkAt<List<Int>>("nested", "testing")
        assertEquals(listOf(1, 2, 3, 4), configChunk)
    }

    @Test
    fun `test merged configuration - no conflicts`() {
        val fileA = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "a": "a property"
                }
            """.trimIndent()
            )
        }

        val fileB = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "b": "b property"
                }
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", fileA.absolutePath, "--config", fileB.absolutePath))
        micro.install(ConfigurationFeature)

        assertEquals("a property", micro.configuration.requestChunkAt("a"))
        assertEquals("b property", micro.configuration.requestChunkAt("b"))
    }

    @Test
    fun `test merged configuration - in same subtree`() {
        val fileA = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "tree": {
                    "a": "a property"
                  }
                }
            """.trimIndent()
            )
        }

        val fileB = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "tree": {
                    "b": "b property"
                  }
                }
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", fileA.absolutePath, "--config", fileB.absolutePath))
        micro.install(ConfigurationFeature)

        assertEquals("a property", micro.configuration.requestChunkAt("tree", "a"))
        assertEquals("b property", micro.configuration.requestChunkAt("tree", "b"))
    }

    @Test
    fun `test merged configuration - lists`() {
        val fileA = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "tree": {
                    "a": [1, 2, 3]
                  }
                }
            """.trimIndent()
            )
        }

        val fileB = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "tree": {
                    "a": [4, 5, 6]
                  }
                }
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", fileA.absolutePath, "--config", fileB.absolutePath))
        micro.install(ConfigurationFeature)

        assertEquals(listOf(1, 2, 3, 4, 5, 6), micro.configuration.requestChunkAt("tree", "a"))
    }

    @Test
    fun `test merged configuration - mixed languages`() {
        val fileA = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "tree": {
                    "a": "a property"
                  }
                }
            """.trimIndent()
            )
        }

        val fileB = Files.createTempFile("config", ".yml").toFile().also {
            //language=yaml
            it.writeText(
                """
                ---
                tree:
                  b: b property
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", fileA.absolutePath, "--config", fileB.absolutePath))
        micro.install(ConfigurationFeature)

        assertEquals("a property", micro.configuration.requestChunkAt("tree", "a"))
        assertEquals("b property", micro.configuration.requestChunkAt("tree", "b"))
    }

    @Test
    fun `test merged configuration - delete sub tree`() {
        val fileA = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "tree": {
                    "a": {
                      "b": {
                        "c": 42
                      }
                    }
                  }
                }
            """.trimIndent()
            )
        }

        val fileB = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "tree": "test"
                }
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", fileA.absolutePath, "--config", fileB.absolutePath))
        micro.install(ConfigurationFeature)

        assertEquals("test", micro.configuration.requestChunkAt("tree"))
    }

    @Test
    fun `test manual injection`() {
        val micro = Micro()
        micro.init(description, emptyArray())
        micro.install(ConfigurationFeature)

        val mapper = jacksonObjectMapper()

        //language=json
        val node = mapper.readTree(
            """
            { "a": [1, 2, 3, 4]}
        """.trimIndent()
        )

        micro.feature(ConfigurationFeature).manuallyInjectNode(micro.configuration, node)

        assertEquals(listOf(1, 2, 3, 4), micro.configuration.requestChunkAt("a"))
    }

    @Test
    fun `test unknown extension`() {
        val configFile = Files.createTempFile("config", ".unknown").toFile().also {
            // Unknown file extensions should be interpreted as json
            //language=json
            it.writeText(
                """
                {
                  "testing": [1, 2, 3, 4]
                }
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", configFile.absolutePath))
        micro.install(ConfigurationFeature)

        val configChunk = micro.configuration.requestChunkAt<List<Int>>("testing")
        assertEquals(listOf(1, 2, 3, 4), configChunk)
    }

    @Test
    fun `test missing config file`() {
        val configFile = Files.createTempFile("config", ".json").toFile().also {
            //language=json
            it.writeText(
                """
                {
                  "testing": [1, 2, 3, 4]
                }
            """.trimIndent()
            )
        }

        val micro = Micro()
        micro.init(description, arrayOf("--config", configFile.absolutePath, "--config", "/i/cannot/find/this/file"))
        micro.install(ConfigurationFeature)

        val configChunk = micro.configuration.requestChunkAt<List<Int>>("testing")
        assertEquals(listOf(1, 2, 3, 4), configChunk)
    }
}