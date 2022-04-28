package dk.sdu.cloud.config

import kotlinx.serialization.*

@Serializable
@SerialName("Ticket")
class TicketBasedConnectionConfiguration : ConfigSchema.Plugins.Connection()

