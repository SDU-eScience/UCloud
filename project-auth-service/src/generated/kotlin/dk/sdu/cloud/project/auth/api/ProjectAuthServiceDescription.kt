package dk.sdu.cloud.project.auth.api

import dk.sdu.cloud.client.ServiceDescription

object ProjectAuthServiceDescription : ServiceDescription {
    override val name: String = "project-auth"
    override val version: String = "0.1.0-SNAPSHOT"
}