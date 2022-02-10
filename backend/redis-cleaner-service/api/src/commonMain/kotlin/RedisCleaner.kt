package dk.sdu.cloud.redis.cleaner.api

import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.description

object RedisCleaner : CallDescriptionContainer("rediscleaner") {
    init {
        description = "Management script for cleaning up data in Redis which is older than 5 days."
    }
}