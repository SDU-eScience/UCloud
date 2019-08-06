import com.fasterxml.jackson.module.kotlin.readValue

object Kubernetes {
    fun readResource(type: String, name: String, namespace: String? = null): K8Doc? {
        val content = runProcess("kubectl", "get", "-o", "yaml", type, name, "--namespace", namespace ?: "default")
        if (content.isEmpty()) return null

        return yamlMapper.readValue<K8Doc>(content).cleanDocument()
    }

    private fun runProcess(vararg command: String): String {
        return ProcessBuilder().command(*command).start().inputStream.bufferedReader().readText()
    }
}

