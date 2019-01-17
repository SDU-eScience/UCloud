package dk.sdu.cloud.project.api

import dk.sdu.cloud.client.ServiceDescription

object ProjectServiceDescription : ServiceDescription {
    override val name: String = "project"
    override val version: String = "0.1.0"
}