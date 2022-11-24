package dk.sdu.cloud

data class Service(
    val containerName: String,
    val title: String,
    val logsSupported: Boolean,
    val execSupported: Boolean,
    val useServiceConvention: Boolean,
    val address: String? = null,
    val uiHelp: String? = null,
)

val allServices = ArrayList<Service>()
fun serviceByName(name: String): Service {
    return allServices.find { it.containerName == name } ?: error("No such service: $name")
}

val allVolumeNames = ArrayList<String>()

class ServiceMenu(
    val requireLogs: Boolean = false,
    val requireExec: Boolean = false,
    val requireAddress: Boolean = false,
) : Menu("Select a service") {
    init {
        val filteredServices = allServices
            .filter { !requireLogs || it.logsSupported }
            .filter { !requireExec || it.execSupported }
            .filter { !requireAddress || it.address != null }

        var lastPrefix = ""
        for (service in filteredServices) {
            val myPrefix = service.title.substringBefore(':')
            if (myPrefix != lastPrefix) {
                separator(myPrefix)
                lastPrefix = myPrefix
            }
            item(service.containerName, service.title.substringAfter(": ", ))
        }
    }
}
