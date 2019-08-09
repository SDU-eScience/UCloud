import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

val yamlFactory = YAMLFactory()
val yamlMapper by lazy { createYamlMapper() }

private fun createYamlMapper(): ObjectMapper {
    return ObjectMapper(yamlFactory).registerKotlinModule()
}
