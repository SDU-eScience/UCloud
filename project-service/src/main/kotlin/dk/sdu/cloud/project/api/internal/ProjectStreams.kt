package dk.sdu.cloud.project.api.internal

import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.service.KafkaDescriptions

object ProjectStreams : KafkaDescriptions() {
    val ProjectEvents = stream<Long, ProjectEvent>("projectEvents") { it.id }
    val ProjectCommands = ProjectDescriptions.projectCommandBundle.mappedAtGateway("projectCommands") {
        Pair(it.event.id, it)
    }
}