package dk.sdu.cloud.auth.api

import dk.sdu.cloud.service.KafkaDescriptions

object AuthStreams : KafkaDescriptions() {
    val UserUpdateStream = stream<String, UserEvent>("auth.user") { it.key }
    val RefreshTokenStream = stream<String, RefreshTokenEvent>("auth.refreshTokens") { it.key }
    val OneTimeTokenStream = stream<String, OneTimeTokenEvent>("auth.oneTimeTokens") { it.jti }
}