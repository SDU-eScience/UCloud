package dk.sdu.cloud.person.api.internal

import dk.sdu.cloud.person.api.PersonDescriptions
import dk.sdu.cloud.person.api.PersonEvent
import dk.sdu.cloud.person.KafkaDescriptions
import dk.sdu.cloud.person.api.PersonDescriptions

object PersonStreams : KafkaDescriptions() {
    val PersonEvents = stream<Long, PersonEvent>("personEvents") { it.id }
    val PersonCommands = PersonDescriptions.personCommandBundle.mappedAtGateway("personCommands") {
        Pair(it.event.id, it)
    }
}