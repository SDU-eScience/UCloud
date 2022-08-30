package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions

class ListComponent(val driver: WebDriver, val container: WebElement) {
    fun rowCount(): Int = container.findComponents("list-row").size

    fun row(idx: Int): ListRow = container.findComponents("list-row").getOrElse(idx) {
        throw IllegalArgumentException("Cannot request row $idx, only ${rowCount()} exist")
    }.let { ListRow(driver, it) }

    fun rows(): List<ListRow> = (0 until rowCount()).map { row(it) }

    fun cancelInput() {
        var foundInput = false
        for (row in rows()) {
            row.findInputField() ?: continue
            row.cancelInput()
            foundInput = true
        }

        if (!foundInput) error("Could not find input field")
    }

    fun sendInput(keys: String, clear: Boolean = false) {
        var foundInput = false
        for (row in rows()) {
            val input = row.findInputField() ?: continue
            if (clear) input.clear()
            input.sendKeys(keys)
            foundInput = true
        }

        if (!foundInput) error("Could not find input field")
    }

    fun findProductSelector(): WebElement? {
        return container.findComponentOrNull("product-selector")
    }

    data class Product(
        val element: WebElement,
        val name: String,
        val price: String?,
    ) {
        fun select() {
            element.click()
        }
    }

    fun products(): List<Product> {
        val dropdown = driver.findComponentOrNull("product-selector-dropdown")
            ?: error("Could not find active dropdown for products")

        return dropdown.findElements(By.tagName("tr")).mapNotNull { row ->
            val columns = row.findElements(By.tagName("td"))
            if (columns.size < 1) return@mapNotNull null

            Product(row, columns[0].text, columns.getOrNull(1)?.text)
        }
    }

    companion object {
        fun inModal(driver: WebDriver): ListComponent {
            val firstRow = driver.findElementOrNull(By.cssSelector(".ReactModalPortal [data-component=list-row]"))
                ?: error("Could not locate list")
            return ListComponent(driver, firstRow.parent().parent())
        }

        fun inMainContainer(driver: WebDriver): ListComponent {
            return ListComponent(driver, driver.mainContainer)
        }
    }
}

class ListRow(val driver: WebDriver, val row: WebElement) {
    fun isSelected(): Boolean {
        return row.getAttribute("data-selected") == "true"
    }

    suspend fun select() {
        row.findElementOrNull(By.cssSelector(".row-icon"))?.click()
        await { isSelected() }
    }

    fun icon(): String? {
        return row.findElementOrNull(By.cssSelector(".row-icon svg"))?.getAttribute("data-component")
            ?.removePrefix("icon-")
    }

    fun title(): String {
        return row.findElementOrNull(By.cssSelector(".row-left-content"))?.text ?: error("No title found")
    }

    data class Substat(val icon: String?, val stat: String)

    fun substats(): List<Substat> {
        TODO()
    }

    fun navigate() {
        row.findElementOrNull(By.cssSelector(".row-left-content"))?.click()
    }

    fun importantStats(): WebElement? {
        return row.findElementOrNull(By.cssSelector(".row-right"))
    }

    fun openOperations(useRightClick: Boolean) {
        if (useRightClick) {
            val target = row.findElementOrNull(By.cssSelector(".row-left")) ?: error("Row does not exist")
            Actions(driver).contextClick(target).perform()
        } else {
            val target = row.findElementOrNull(By.cssSelector(".row-right span")) ?: error("Operations does not exist")
            target.click()
        }
    }

    fun selectOperation(text: String, hold: Boolean = false) {
        val op =
            driver.findElementOrNull(By.cssSelector("[data-tag=${text.replace(".", "").replace(" ", "_")}-action]"))
                ?: error("Operation not found: '$text'")
        if (hold) {
            Actions(driver).clickAndHold(op).pause(3000).release().perform()
        } else {
            op.click()
        }
    }

    fun findInputField(): WebElement? {
        return row.findElementOrNull(By.cssSelector("input"))
    }

    fun cancelInput() {
        (findInputField() ?: error("No active input")).sendKeys("$ESCAPE")
    }

    fun use() {
        row.clickUniqueButton("Use")
    }

    companion object {
        private val ESCAPE = Char(27)
    }
}
