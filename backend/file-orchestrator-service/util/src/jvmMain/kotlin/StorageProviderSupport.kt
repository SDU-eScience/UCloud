package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.util.ProviderSupport
import dk.sdu.cloud.file.orchestrator.api.*

typealias StorageProviderSupport = ProviderSupport<StorageCommunication, Product.Storage, FSSupport>
