package dk.sdu.cloud.transactions

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

data class ServiceManifest(
        val name: String,
        val version: String
)

class ServiceClassLoader(private val jarFile: JarFile) : ClassLoader(), Closeable {
    val manifest: ServiceManifest
    val source = jarFile.name!!
    private val _knownResources: Set<String>
    private val _knownClasses: Map<String, JarEntry>

    val knownClasses: List<String> get() = _knownClasses.keys.toList()

    private val cachedClasses = HashMap<String, Class<*>>()
    companion object {
        private val mapper = jacksonObjectMapper()
        private val log = LoggerFactory.getLogger(ServiceClassLoader::class.qualifiedName)
    }

    init {
        val manifestEntry = jarFile.getJarEntry("service_manifest.json")
        manifest = if (manifestEntry != null) {
            mapper.readValue(jarFile.getInputStream(manifestEntry))
        } else {
            log.warn("Missing service manifest for service ${jarFile.name}")
            ServiceManifest(jarFile.name, "1.0.0")
        }

        val entries = jarFile.entries().toList()
        _knownResources = entries.map { it.name }.toSet()

        _knownClasses = entries.filter { it.name.endsWith(".class") }.map {
            Pair(it.name.replace('/', '.').removeSuffix(".class"), it)
        }.toMap()
    }

    override fun loadClass(name: String): Class<*>? {
        return when (name) {
            in cachedClasses -> cachedClasses[name]

            in _knownClasses -> {
                val readBytes = jarFile.getInputStream(_knownClasses[name]).readBytes()
                defineClass(name, readBytes, 0, readBytes.size).also {
                    cachedClasses[name] = it
                }
            }

            else -> parent.loadClass(name)
        }
    }

    override fun getResourceAsStream(name: String): InputStream? {
        if (name in _knownResources) {
            val jarEntry = jarFile.getJarEntry(name) ?: return null
            return jarFile.getInputStream(jarEntry)
        }
        return null
    }

    override fun close() {
        jarFile.close()
    }
}
