package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.ProductsRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.SimpleCache

data class ResolvedJobResources(
    val product: Product.Compute,
    val application: Application,
)

class ResourceCache(private val k8: K8Dependencies) {
    private val products = SimpleCache<String, Product.Compute>(
        maxAge = 1000 * 60 * 60 * 24L,
        lookup = { null }
    )
    private val applications = SimpleCache<String, Application>(
        maxAge = 1000 * 60 * 60 * 24 * 100L,
        lookup = { null }
    )

    private suspend fun cache(job: Job) {
        val productKey = key(job.specification.product)
        if (products.get(productKey) == null) {
            val resolvedProduct = Products.retrieve.call(
                ProductsRetrieveRequest(
                    filterName = job.specification.product.id,
                    filterCategory = job.specification.product.category,
                    filterProvider = job.specification.product.provider,
                    filterArea = ProductType.COMPUTE
                ),
                k8.serviceClient
            ).orThrow() as Product.Compute

            products.insert(productKey, resolvedProduct)
        }

        val resolvedApplication = job.status.resolvedApplication
        if (resolvedApplication != null) {
            applications.insert(key(job.specification.application), resolvedApplication)
        }
    }

    suspend fun findResources(job: Job, allowNetwork: Boolean = true): ResolvedJobResources {
        val product = products.get(key(job.specification.product))
        val application = applications.get(key(job.specification.application))

        if (product != null && application != null) {
            return ResolvedJobResources(product, application)
        }

        if (!allowNetwork) error("Could not find resources for ${job.id}")

        val retrievedJob = JobsControl.retrieve.call(
            ResourceRetrieveRequest(JobIncludeFlags(includeApplication = true), job.id),
            k8.serviceClient
        ).orThrow()

        cache(retrievedJob)
        return findResources(job, allowNetwork = false)
    }

    private fun key(product: ComputeProductReference): String {
        return "${product.id}/${product.provider}"
    }

    private fun key(application: NameAndVersion): String {
        return "${application.name}/${application.version}"
    }
}
