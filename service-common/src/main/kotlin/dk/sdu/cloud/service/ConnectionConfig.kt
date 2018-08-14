package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription

data class DatabaseConfiguration(
    val host: String,
    val database: String,
    val username: String,
    val password: String
) {
    override fun toString(): String {
        return "DatabaseConfiguration(host='$host', database='$database', username='$username')"
    }
}

data class ServiceConnectionConfig(
    val description: ServiceDescription,
    val hostname: String,
    val port: Int
)
