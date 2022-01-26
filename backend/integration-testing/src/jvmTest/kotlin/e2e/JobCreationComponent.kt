package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.integration.retrySection
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement

class JobCreationComponent(private val driver: WebDriver) {
    // Reservation
    fun jobNameInput(): WebElement = driver.findElementOrNull(By.id("reservation-name")) ?: error("No job name input")
    fun hoursInput(): WebElement = driver.findElementOrNull(By.id("reservation-hours")) ?: error("No hours")
    fun minutesInput(): WebElement = driver.findElementOrNull(By.id("reservation-minutes")) ?: error("No minutes")
    fun replicasInput(): WebElement? = driver.findElementOrNull(By.id("reservation-replicas"))

    // Machines
    data class MachineType(
        val row: WebElement,
        val name: String,
        val vcpu: String,
        val ram: String,
        val price: String
    ) {
        fun select() {
            row.click()
        }
    }

    suspend fun openMachineTypes() {
        (driver.findComponentOrNull("machines") ?: error("Could not find machines")).click()
        retrySection {
            require(machineTable() != null) { "Did not manage to open the machine table (table not found)" }
        }
    }

    fun machineTable(): WebElement? = driver.findComponentOrNull("machine-table")

    fun machineTypes(): List<MachineType> {
        val table = machineTable() ?: error("No machine table found")
        return table.findElements(By.tagName("tr")).mapNotNull { row ->
            val columns = row.findElements(By.tagName("td"))
            // NOTE(Dan): This is the case for the header (which uses th instead)
            if (columns.size < 4) return@mapNotNull null
            MachineType(
                row,
                columns[0].text,
                columns[1].text,
                columns[2].text,
                columns[3].text,
            )
        }
    }

    // Parameters
    fun findParameters(): List<InputParameter> {
        return driver.findComponents("app-parameter").map { InputParameter.createFromRoot(it) }
    }

    fun addFolder() {
        driver.clickUniqueButton("Add folder")
    }

    fun addPeer() {
        driver.clickUniqueButton("Connect to job")
    }

    fun addPublicIpAddress() {
        driver.clickUniqueButton("Add public IP")
    }

    fun addPublicLink() {
        driver.clickUniqueButton("Add public link")
    }

    // Sidebar options
    suspend fun submit() {
        val startUrl = driver.currentUrl
        driver.clickUniqueButton("Submit")
        await { driver.currentUrl != startUrl }
    }
}

sealed class InputParameter(val element: WebElement) {
    fun title(): String {
        return (element.findComponentOrNull("param-title") ?: error("No title found")).text.removeSuffix("*").trim()
    }

    fun optional(): Boolean {
        return (element.findComponentOrNull("param-title") ?: error("No title found")).text.endsWith("*")
    }

    fun remove() {
        element.findComponentOrNull("param-remove")?.click() ?: error("Could not remove parameter")
    }

    fun canRemove(): Boolean {
        return element.findComponentOrNull("param-remove") != null
    }

    fun isOpen(): Boolean {
        return element.findElements(By.tagName("button")).none { it.text == "Use" }
    }

    fun use() {
        element.clickUniqueButton("Use")
    }

    class Text(element: WebElement) : InputParameter(element) {
        fun input(): WebElement {
            return element.findElementOrNull(By.tagName("input")) ?: error("Could not find input field")
        }
    }

    class TextArea(element: WebElement) : InputParameter(element) {
        fun input(): WebElement {
            return element.findElementOrNull(By.tagName("textarea")) ?: error("Could not find input field")
        }
    }

    class File(element: WebElement) : InputParameter(element) {
        fun open() {
            element.findElements(By.tagName("input")).find { it.getAttribute("id").endsWith("visual") }
                ?.click() ?: error("Could not find input")
        }
    }

    class Bool(element: WebElement) : InputParameter(element) {
        private fun selectElement(): WebElement {
            return element.findElementOrNull(By.tagName("select")) ?: error("Could not find selector")
        }

        fun value(): Boolean? {
            return when (selectElement().getAttribute("value")) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }

        fun setValue(value: Boolean) {
            selectElement().sendKeys(value.toString())
        }
    }

    class Ingress(element: WebElement) : InputParameter(element) {
        fun open() {
            element.findElements(By.tagName("input")).find { it.getAttribute("id").endsWith("visual") }
                ?.click() ?: error("Could not find input")
        }
    }

    companion object {
        fun createFromRoot(element: WebElement): InputParameter {
            return when (val attribute = element.getAttribute("data-param-type")) {
                "text", "floating_point", "integer" -> Text(element)
                "textarea" -> TextArea(element)
                "input_directory", "input_file" -> File(element)
                "boolean" -> Bool(element)
                "ingress" -> Ingress(element)
                else -> TODO("Unknown attribute: '$attribute'")
            }
        }
    }
}
