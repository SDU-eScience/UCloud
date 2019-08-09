import com.fasterxml.jackson.module.kotlin.readValue

object Kubernetes {
    fun readResource(type: String, name: String, namespace: String? = null, context: String? = null): K8Doc? {
        val command = arrayListOf("kubectl", "get", "-o", "yaml", type, name)

        if (namespace != null) command.addAll(listOf("--namespace", namespace))
        if (context != null) command.addAll(listOf("--context", context))

        val content = runProcess(*command.toTypedArray())
        if (content.isEmpty()) return null

        return yamlMapper.readValue<K8Doc>(content).cleanDocument()
    }

    private fun runProcess(vararg command: String): String {
        return ProcessBuilder().command(*command).start().inputStream.bufferedReader().readText()
    }
}

