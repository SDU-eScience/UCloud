package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.WebElement

class ListComponent(val container: WebElement) {
    fun rowCount(): Int { TODO() }
    fun row(idx: Int): ListRow { TODO() }
}

class ListRow(val row: WebElement) {
    fun isSelected(): Boolean { TODO() }
    fun select() { TODO() }
    fun icon(): String? { TODO() }
    fun title(): String? { TODO() }
    data class Substat(val icon: String?, val stat: String)
    fun substats(): List<Substat> { TODO() }
    fun navigate() { TODO() }
    fun importantStats(): WebElement? { TODO() }
    fun openOperations(useRightClick: Boolean) { TODO() }
}
