package dk.sdu.cloud.auth.api

import org.esciencecloud.service.KafkaDescriptions

object AuthStreams : KafkaDescriptions() {
    val UserUpdateStream = stream<String, UserEvent>("auth.user")
    val RefreshTokenStream = stream<String, RefreshTokenEvent>("auth.refreshTokens")
}