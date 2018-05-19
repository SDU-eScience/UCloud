package dk.sdu.cloud.tus.api

import dk.sdu.cloud.client.ServiceDescription

object TusServiceDescription : ServiceDescription {
    override val name: String = "tus"
    override val version: String = "1.0.0-SNAPSHOT"
}