package dk.sdu.cloud.filesearch.api

import dk.sdu.cloud.client.ServiceDescription

object FilesearchServiceDescription : ServiceDescription {
    override val name: String = "filesearch"
    override val version: String = "0.1.3-SNAPSHOT"
}