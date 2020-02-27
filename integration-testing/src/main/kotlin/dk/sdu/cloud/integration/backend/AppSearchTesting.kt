package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.app.store.api.AdvancedSearchRequest
import dk.sdu.cloud.app.store.api.AppSearchRequest
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page

class AppSearchTesting(private val user: UserAndClient) {

    suspend fun runTest() {
        simpleSearch()
        advancedSearch()
    }

    // We don't test for version as this could break for some reason. Seems more stable to look for name.
    private suspend fun simpleSearch() {
        log.info("Simple Search - With part of name")
        val result = AppStore.searchApps.call(
            AppSearchRequest("fig", 100, 0), user.client
        ).orThrow()

        check(result.items.isNotEmpty()) { "Expected at least one app in simple search." }
        checkContainsName(result, "fig")

        log.info("Simple Search - With full name")
        val sameResult = AppStore.searchApps.call(
            AppSearchRequest("figlet", 100, 0), user.client
        ).orThrow()

        check(sameResult.items.isNotEmpty() && sameResult.itemsInTotal <= result.itemsInTotal) {
            "Expected result to be non-empty, and previous result to have more or equal app count."
        }
        checkContainsName(sameResult, "figlet")

        log.info("Simple Search - With wrong name")
        val nonFigletResult = AppStore.searchApps.call(
            AppSearchRequest("feglit", 100, 0), user.client
        ).orThrow()

        check(nonFigletResult.items.all { !it.metadata.name.toLowerCase().contains("figlet") }) {
            "Expected no apps with name 'figlet'."
        }
    }

    private fun checkContainsName(page: Page<ApplicationSummaryWithFavorite>, expectedName: String) {
        page.items.forEach { (metadata) ->
            // Intentionally, do it like this so whe have the name that fails.
            // Alternative would be check(result.items.all { it.metadata.name.contains(appName) }, /* */)
            check(metadata.name.contains(expectedName)) { "App ${metadata.name} does not contain $expectedName" }
        }
    }

    private suspend fun advancedSearch() {
        log.info("Advanced search")

        log.info("No parameters for query")
        val emptyResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest(null, null, false, 100, 0), user.client
        ).orThrow()

        check(emptyResult.items.isEmpty()) { "Expected result to be empty" }

        log.info("With name")
        val byNameResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest("figlet", null, false, 100, 0), user.client
        ).orThrow()

        check(byNameResult.itemsInTotal > 0) { "Expected at least one app" }

        log.info("With tags")
        val byTagResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest(null, listOf("figlet"), false, 100, 0), user.client
        ).orThrow()

        check(byTagResult.itemsInTotal > 0) { "Expected at least one app" }

        log.info("With name and tag")
        val byNameAndTagResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest("figlet", listOf("figlet"), false, 100, 0), user.client
        ).orThrow()

        check(byNameAndTagResult.itemsInTotal > 0) { "Expected at least one app" }

        log.info("With name, tag, and all versions")
        val byNameAndTagAndVersionsResult = AppStore.advancedSearch.call(
            AdvancedSearchRequest("figlet", listOf("figlet"), true, 100, 0), user.client
        ).orThrow()

        check(byNameAndTagAndVersionsResult.itemsInTotal > byNameAndTagResult.itemsInTotal) {
            "Expected more apps when getting all versions."
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}