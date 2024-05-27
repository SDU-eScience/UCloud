package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.app.orchestrator.api.Ingresses
import dk.sdu.cloud.app.orchestrator.api.Jobs
import dk.sdu.cloud.app.orchestrator.api.Licenses
import dk.sdu.cloud.app.orchestrator.api.NetworkIPs
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.file.orchestrator.api.FileCollections
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.project.api.v2.ProjectsBrowseRequest
import dk.sdu.cloud.provider.api.Provider
import dk.sdu.cloud.provider.api.ProviderIncludeFlags
import dk.sdu.cloud.provider.api.Providers

suspend fun findProducts(): Set<ProductV2> {
    val products = HashSet<ProductV2>()
    var next: String? = null
    while (true) {
        val page = ProductsV2.browse.call(
            ProductsV2BrowseRequest(
                itemsPerPage = 250,
                next = next
            ),
            adminClient
        ).orThrow()

        page.items.forEach { products.add(it) }
        next = page.next ?: break
    }
    return products
}

suspend fun findProductCategories(): Set<ProductCategory> {
    return findProducts().map { it.category }.toSet()
}

suspend fun retrieveProviderProjectId(): String {
    var next: String? = null

    val adminProjects = HashSet<String>()
    while (true) {
        val page = Projects.browse.call(
            ProjectsBrowseRequest(
                itemsPerPage = 250,
                next = next
            ),
            adminClient
        ).orThrow()

        val filtered = page.items.filter { it.specification.title == UCLOUD_PROVIDER}
        filtered.forEach { adminProjects.add(it.id) }
        next = page.next ?: break
    }

    if (adminProjects.isEmpty()) {
        throw IllegalStateException()
    }

    return adminProjects.first()
}

suspend fun findProviders(): Set<Provider> {
    // NOTE(Dan): Assume that the providers are all registered with the admin client. This is generally true when
    // created through the launcher.
    val result = HashSet<Provider>()
    var next: String? = null

    val adminProjects = HashSet<String>()
    while (true) {
        val page = Projects.browse.call(
            ProjectsBrowseRequest(
                itemsPerPage = 250,
                next = next
            ),
            adminClient
        ).orThrow()

        page.items.forEach { adminProjects.add(it.id) }
        next = page.next ?: break
    }

    for (project in adminProjects) {
        next = null
        while (true) {
            val page = Providers.browse.call(
                ResourceBrowseRequest(
                    ProviderIncludeFlags(
                        filterProvider = UCLOUD_PROVIDER
                    ),
                    itemsPerPage = 250,
                    next = next
                ),
                adminClient.withProject(project)
            ).orThrow()

            page.items.forEach { result.add(it) }

            next = page.next ?: break
        }
    }

    return result
}

suspend fun findProviderIds(): Set<String> {
    return findProviders().map { it.specification.id }.filter { providerId ->
        !(providerId == "slurm" && System.getenv("ENABLE_SLURM_TEST") == null)
    }.toSet()
}

suspend fun <P : Product, S : ProductSupport> findSupport(
    product: P,
    project: String?,
    client: AuthenticatedClient
): ResolvedSupport<P, S> {
    @Suppress("UNCHECKED_CAST")
    val resourceApi: ResourceApi<*, *, *, *, *, P, S> = when (product) {
        is Product.Storage -> FileCollections as ResourceApi<*, *, *, *, *, P, S>
        is Product.Compute -> Jobs as ResourceApi<*, *, *, *, *, P, S>
        is Product.Ingress -> Ingresses as ResourceApi<*, *, *, *, *, P, S>
        is Product.License -> Licenses as ResourceApi<*, *, *, *, *, P, S>
        is Product.NetworkIP -> NetworkIPs as ResourceApi<*, *, *, *, *, P, S>
        else -> error("unknown product type $product")
    }

    val products = resourceApi.retrieveProducts.call(
        Unit,
        if (project == null) client else client.withProject(project)
    ).orThrow()

    return products.productsByProvider.values.flatten().find { it.product.toReference() == product.toReference() }
        ?: error("Could not find product support: ${product.toReference()}")
}
