package dk.sdu.cloud.app.orchestrator.services

import java.io.Serializable

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable
