package org.esciencecloud.auth.api

import org.esciencecloud.service.KafkaDescriptions

object RefreshTokenStreams : KafkaDescriptions() {
    val RefreshTokenStream = stream<String, RefreshTokenEvent>("auth.refreshTokens")
}