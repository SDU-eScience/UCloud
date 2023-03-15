package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.integration.backend.compute.toReference

data class InitializedCollection(
    val collection: FileCollection,
    val product: Product.Storage,
    val support: FSSupport,
)

suspend fun initializeCollection(
    project: String,
    rpcClient: AuthenticatedClient,
    provider: String,
    productFilter: Product? = null,
): InitializedCollection? {
    println("initialize")
    val allSupport = FileCollections.retrieveProducts
        .call(Unit, rpcClient)
        .orThrow()
        .productsByProvider.values
        .flatten()
        .filter { it.product.category.provider == provider}
    println("allSupport: $allSupport")

    val chosenProductWithSupport = allSupport
        .find {
            (productFilter == null || it.product.toReference() == productFilter.toReference()) &&
                it.support.collection.usersCanCreate == true
        } ?: allSupport.find {
            !it.support.files.isReadOnly &&
                it.product.name != "syncthing" &&
                it.product.name != "share" &&
                it.product.name != "project-home"
        } ?: return null
    println("chosen: $chosenProductWithSupport")

    val product = chosenProductWithSupport.product
    val support = chosenProductWithSupport.support

    if (productFilter != null && chosenProductWithSupport.product.toReference() != productFilter.toReference()) {
        error("Could not select product: $product could not find support info")
    }

    println("After filter")
    // NOTE(Dan): Don't throw since we didn't even check if we should do this.
    // At least one of these should initialize the system for us. If we still end up with no
    // collection, then something is wrong in the system.
    FileCollections.init.call(Unit, rpcClient)
    Files.init.call(Unit, rpcClient)
    println("OKAY")
    if (support.collection.usersCanCreate == true) {
        FileCollections.create.call(
            bulkRequestOf(FileCollection.Spec(generateId("Drive"), product.toReference())),
            rpcClient
        )
    }
    println("Afterinits")
    val collection = retrySection(attempts = 30, delay = 1000) {
        FileCollections.browse
            .call(
                ResourceBrowseRequest(FileCollectionIncludeFlags()),
                rpcClient
            )
            .orThrow()
            .items
            .firstOrNull() ?: throw IllegalStateException("No collection found for: $project of $product")
    }
    println("COLLECTION: $collection")

    val createdCollectionWithSupport = allSupport.find { it.product.toReference() == collection.specification.product }
        ?: error("Unknown product")

    println("Created: $createdCollectionWithSupport")
    retrySection(attempts = 30, delay = 1000) {
        Files.browse.call(
            ResourceBrowseRequest(
                UFileIncludeFlags(path = "/${collection.id}"),
                itemsPerPage = 250
            ),
            rpcClient
        ).orThrow()
    }
    println("COULD BRTOSE")
    return InitializedCollection(
        collection,
        createdCollectionWithSupport.product,
        createdCollectionWithSupport.support
    )
}
