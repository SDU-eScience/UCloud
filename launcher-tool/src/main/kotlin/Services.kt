package dk.sdu.cloud

data class Service(
    val containerName: String,
    val title: String,
    val logsSupported: Boolean,
    val execSupported: Boolean,
    val useServiceConvention: Boolean,
    val address: String? = null,
)

val allServices = ArrayList<Service>()
fun serviceByName(name: String): Service {
    return allServices.find { it.containerName == name } ?: error("No such service: $name")
}

class ServiceMenu(
    val requireLogs: Boolean = false,
    val requireExec: Boolean = false,
    val requireAddress: Boolean = false,
) : Menu("Select a service") {
    init {
        allServices
            .filter { !requireLogs || it.logsSupported }
            .filter { !requireExec || it.execSupported }
            .filter { !requireAddress || it.address != null }
            .forEach { item(it.containerName, it.title) }
    }
}
