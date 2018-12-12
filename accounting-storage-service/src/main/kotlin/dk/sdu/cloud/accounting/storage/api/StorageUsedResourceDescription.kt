package dk.sdu.cloud.accounting.storage.api

import dk.sdu.cloud.accounting.api.AbstractAccountingResourceDescriptions

object StorageUsedResourceDescription :
    AbstractAccountingResourceDescriptions<StorageUsedEvent>(namespace, "bytesUsed")
