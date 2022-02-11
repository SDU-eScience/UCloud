package dk.sdu.cloud.redis.cleaner.api

import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.InternalLevel
import dk.sdu.cloud.calls.UCloudApiInternal
import dk.sdu.cloud.calls.description

@UCloudApiInternal(InternalLevel.STABLE)
object RedisCleaner : CallDescriptionContainer("rediscleaner") {
    init {
        description = """
            Management script.
            
            Management script for cleaning up data in Redis which is older than 5 days.
        """.trimIndent()
    }
}