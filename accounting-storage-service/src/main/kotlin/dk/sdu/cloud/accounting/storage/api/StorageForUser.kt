package dk.sdu.cloud.accounting.storage.api

import java.util.*

data class StorageForUser(
    val id: Long,
    val user: String,
    val date: Date,
    val usage: Long
)
