package org.esciencecloud.auth.api

import org.esciencecloud.service.KafkaDescriptions

object AuthStreams : KafkaDescriptions() {
    val UserUpdateStream = stream<String, UserEvent>("auth.user")
}