package dk.sdu.cloud.accounting.storage.api

import dk.sdu.cloud.accounting.api.AccountingEvent

data class StorageUsedEvent(
    override val timestamp: Long,
    val bytesUsed: Long,
    val id: Long,
    val user: String
) : AccountingEvent {
    override val title: String = "Storage used"
    override val description: String? = null
}
