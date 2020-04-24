package dk.sdu.cloud.accounting.storage.api

import dk.sdu.cloud.accounting.api.AbstractAccountingResourceDescriptions

internal const val namespace = "storage"
object StorageUsedResourceDescription : AbstractAccountingResourceDescriptions<StorageUsedEvent>(namespace, "bytesUsed")
