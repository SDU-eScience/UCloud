package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.events.RedisConnectionManager
import dk.sdu.cloud.events.RedisStreamService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.findValidHostname
import io.lettuce.core.RedisClient
import org.slf4j.Logger

data class RedisConfiguration(
    val hostname: String? = null
)

class RedisFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)
        log.info("Connecting to redis")

        val userConfig = ctx.configuration.requestChunkAtOrNull("redis") ?: RedisConfiguration()
        val hostname = userConfig.hostname?.takeIf { it.isNotEmpty() } ?: run {
            log.info("No available configuration found at 'redis/hostname'.")
            log.info("Attempting to look for defaults.")

            val hostname = findValidHostname(defaultHostNames)
                ?: throw IllegalStateException("Could not find a valid redis host")

            log.info("$hostname is a valid host, assuming redis is running on this machine.")

            hostname
        }

        log.info("Connected to redis")

        ctx.redisConnectionManager = RedisConnectionManager(RedisClient.create("redis://$hostname"))

        ctx.eventStreamService = RedisStreamService(
            ctx.redisConnectionManager,
            ctx.serviceInstance.hostname
        )

        ctx.featureOrNull(DeinitFeature)?.addHandler {
            ctx.eventStreamService.stop()
        }
    }

    companion object Feature : MicroFeatureFactory<RedisFeature, Unit>, Loggable {
        override val log: Logger = logger()
        override val key: MicroAttributeKey<RedisFeature> = MicroAttributeKey("redis-feature")
        override fun create(config: Unit): RedisFeature = RedisFeature()

        private val defaultHostNames = listOf("redis", "localhost")
    }
}

private val redisConnectionManagerKey = MicroAttributeKey<RedisConnectionManager>("redis-connection-manager")
var Micro.redisConnectionManager: RedisConnectionManager
    get() {
        return attributes[redisConnectionManagerKey]
    }
    set(value) {
        attributes[redisConnectionManagerKey] = value
    }

