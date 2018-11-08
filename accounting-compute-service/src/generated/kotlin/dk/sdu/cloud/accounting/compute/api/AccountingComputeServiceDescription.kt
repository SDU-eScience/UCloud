package dk.sdu.cloud.accounting.compute.api

import dk.sdu.cloud.client.ServiceDescription

object AccountingComputeServiceDescription : ServiceDescription {
    override val name: String = "accounting-compute"
    override val version: String = "0.2.0-SNAPSHOT"
}