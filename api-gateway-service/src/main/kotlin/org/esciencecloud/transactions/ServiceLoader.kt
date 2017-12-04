package org.esciencecloud.transactions

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
        val restEndpoints: List<String>? = null,
        val kafkaProxies: List<String>? = null
)

class ServiceLoader(private val jarFile: JarFile) : ClassLoader(), Closeable {
    private val knownResources: Set<String>
    private val knownClasses: Map<String, JarEntry>
    private val cachedClasses = HashMap<String, Class<*>>()
    val manifest: ServiceManifest

    companion object {
        private val mapper = jacksonObjectMapper()
        private val log = LoggerFactory.getLogger(ServiceLoader::class.qualifiedName)
    }

    init {
        val manifestEntry = jarFile.getJarEntry("service_manifest.json")
        manifest = if (manifestEntry != null) {
            mapper.readValue(jarFile.getInputStream(manifestEntry))
        } else {
            log.warn("Missing service manifest for service ${jarFile.name}")
            ServiceManifest(jarFile.name)
        }

        val entries = jarFile.entries().toList()
        knownResources = entries.map { it.name }.toSet()

        knownClasses = entries.filter { it.name.endsWith(".class") }.map {
            Pair(it.name.replace('/', '.').removeSuffix(".class"), it)
        }.toMap()
    }

    override fun loadClass(name: String): Class<*>? {
        return when (name) {
            in cachedClasses -> cachedClasses[name]

            in knownClasses -> {
                val readBytes = jarFile.getInputStream(knownClasses[name]).readBytes()
                defineClass(name, readBytes, 0, readBytes.size).also {
                    cachedClasses[name] = it
                }
            }

            else -> parent.loadClass(name)
        }
    }

    override fun getResourceAsStream(name: String): InputStream? {
        if (name in knownResources) {
            val jarEntry = jarFile.getJarEntry(name) ?: return null
            return jarFile.getInputStream(jarEntry)
        }
        return null
    }

    override fun close() {
        jarFile.close()
    }
}

fun main(args: Array<String>) {
    val path = "/Users/dthrane/Dropbox/work/sdu-cloud/app-service/build/libs/app-service-api-1.0-SNAPSHOT.jar"

    val s = Scanner(System.`in`)
    while (s.nextLine() != null) {
        println("Trying!")
        try {
            val f = JarFile(File(path))
            ServiceLoader(f).use { loader ->
                println(loader.manifest)
                val klass = loader.loadClass("org.esciencecloud.abc.api.SimpleTest")
                val instance = klass!!.newInstance()
                klass.getMethod("a").invoke(instance)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}