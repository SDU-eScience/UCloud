package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.app.store.api.AdvancedSearchRequest
import dk.sdu.cloud.app.store.api.AppSearchRequest
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Page

class AppSearchTesting(private val user: UserAndClient) {

    suspend fun runTest() {
        simpleSearch()
        advancedSearch()
    }

    // We don't test for version as this could break for some reason. Seems more stable to look for name.
    private suspend fun simpleSearch() {
        val result = AppStore.searchApps.call(
            AppSearchRequest("fig", 25, 0), user.client
        ).orThrow()

        check(result.items.isNotEmpty()) { "Expected at least one app in simple search." }
        checkContainsName(result, "fig")

        val sameResult = AppStore.searchApps.call(
            AppSearchRequest("figlet", 25, 0), user.client
        ).orThrow()

        check(sameResult.items.isNotEmpty() && sameResult.itemsInTotal == result.itemsInTotal) {
            "Expected at least one app in simple search."
        }
        checkContainsName(sameResult, "figlet")

        val emptyResult = AppStore.searchApps.call(
            AppSearchRequest("feglit", 25, 0), user.client
        ).orThrow()

        check(emptyResult.items.isEmpty()) { "Expected no apps in simple search." }
    }

    private fun checkContainsName(page: Page<ApplicationSummaryWithFavorite>, expectedName: String) {
        page.items.forEach { (metadata) ->
            // Intentionally, do it like this so whe have the name that fails.
            // Alternative would be check(result.items.all { it.metadata.name.contains(appName) }, /* */)
            check(metadata.name.contains(expectedName)) { "App ${metadata.name} does not contain $expectedName" }
        }
    }

    // Søg med navn, med tags, med og uden versions,
    private suspend fun advancedSearch() {
        val emptyResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest(null, null, false, 25, 0), user.client
        ).orThrow()

        val byNameResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest("figlet", null, false, 25, 0), user.client
        ).orThrow()

        val byTagResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest(null, listOf("figlet"), false, 25, 0), user.client
        ).orThrow()

        val byNameAndTagResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest("figlet", listOf("figlet"), false, 25, 0), user.client
        ).orThrow()

        val byNameAndTagAndVersionsResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest("figlet", listOf("figlet"), true, 25, 0), user.client
        ).orThrow()

        check(emptyResult.items.isEmpty()) { "Expected result to be empty" }
    }
}