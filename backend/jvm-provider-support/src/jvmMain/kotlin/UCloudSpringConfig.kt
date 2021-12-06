package dk.sdu.cloud.providers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.service.Loggable
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
class UCloudSpringConfig(
    private val interceptor: UCloudAuthInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor).addPathPatterns("/ucloud/**")
    }

    /*
    @Bean
    fun jsonCustomizer(): Jackson2ObjectMapperBuilderCustomizer? {
        return Jackson2ObjectMapperBuilderCustomizer { builder: Jackson2ObjectMapperBuilder ->
            println("Running the customizer!")
            builder.modulesToInstall(KotlinModule())
        }
    }

     */

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper? {
        return ObjectMapper().apply {
            registerKotlinModule()
            configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true)
            configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
