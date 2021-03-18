package dk.sdu.cloud.slack.api

import kotlinx.serialization.Serializable

@Serializable
data class Alert(
    val message: String
)
