package dk.sdu.cloud.accounting.services.notifications

import dk.sdu.cloud.accounting.services.accounting.AccountingRequest
import dk.sdu.cloud.accounting.services.accounting.AccountingSystem
import dk.sdu.cloud.accounting.services.products.ProductService
import dk.sdu.cloud.accounting.services.projects.v2.ProjectService
import dk.sdu.cloud.accounting.util.IdCard
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// See issue #4328 for more information
class PersonalProviderProjects(
    private val projects: ProjectService,
    private val products: ProductService,
    private val accountingSystem: AccountingSystem,
) {
    fun init() {
        projects.addOnPersonalProjectCreated { projects ->
            for (project in projects) {
                val provider = project.status.personalProviderProjectFor ?: continue
                accountingSystem.sendRequest(
                    AccountingRequest.FillUpPersonalProviderProject(
                        IdCard.System,
                        project.id,
                        provider,
                    )
                )
            }
        }

        products.addProductCreatedHandler { products, session ->
            val providers = products.asSequence().map { it.category.provider }.toSet()
            for (provider in providers) {
                val providerProjects = projects.findPersonalProviderProjects(provider)
                for (info in providerProjects) {
                    accountingSystem.sendRequest(
                        AccountingRequest.FillUpPersonalProviderProject(
                            IdCard.System,
                            info.projectId,
                            info.provider,
                        )
                    )
                }
            }
        }

        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch {
            delay(5000)
            val providerProjects = projects.findPersonalProviderProjects(null)
            for (info in providerProjects) {
                accountingSystem.sendRequest(
                    AccountingRequest.FillUpPersonalProviderProject(
                        IdCard.System,
                        info.projectId,
                        info.provider,
                    )
                )
            }
        }
    }
}
