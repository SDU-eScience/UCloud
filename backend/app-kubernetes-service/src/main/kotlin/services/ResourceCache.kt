package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.ComputeProductReference
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobsControlRetrieveRequest
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.SimpleCache

data class ResolvedJobResources(
    val product: Product.Compute,
    val application: Application,
)

class ResourceCache(private val serviceClient: AuthenticatedClient) {
    private val products = SimpleCache<String, Product.Compute>(
        maxAge = 1000 * 60 * 60 * 24L,
        lookup = { null }
    )
    private val applications = SimpleCache<String, Application>(
        maxAge = 1000 * 60 * 60 * 24 * 100L,
        lookup = { null }
    )

    private suspend fun cache(job: Job) {
        val resolvedProduct = job.parameters.resolvedProduct
        if (resolvedProduct != null) {
            products.insert(key(job.parameters.product), resolvedProduct)
        }

        val resolvedApplication = job.parameters.resolvedApplication
        if (resolvedApplication != null) {
            applications.insert(key(job.parameters.application), resolvedApplication)
        }
    }

    suspend fun findResources(job: Job): ResolvedJobResources {
        val product = products.get(key(job.parameters.product))
        val application = applications.get(key(job.parameters.application))

        if (product != null && application != null) {
            return ResolvedJobResources(product, application)
        }

        val retrievedJob = JobsControl.retrieve.call(
            JobsControlRetrieveRequest(job.id, includeProduct = true, includeApplication = true),
            serviceClient
        ).orThrow()

        cache(retrievedJob)

        return ResolvedJobResources(
            retrievedJob.parameters.resolvedProduct ?: error("No product returned"),
            retrievedJob.parameters.resolvedApplication ?: error("No application returned")
        )
    }

    private fun key(product: ComputeProductReference): String {
        return "${product.id}/${product.provider}"
    }

    private fun key(application: NameAndVersion): String {
        return "${application.name}/${application.version}"
    }
}