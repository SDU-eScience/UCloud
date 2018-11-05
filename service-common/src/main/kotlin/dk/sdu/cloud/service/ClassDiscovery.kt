package dk.sdu.cloud.service

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import kotlinx.coroutines.experimental.joinAll
import kotlinx.coroutines.experimental.launch
import java.io.File
import java.io.FileInputStream
import java.util.jar.JarInputStream
import kotlin.reflect.KClass

internal class ClassDiscovery(
    val packagesToLoadFrom: List<String>,
    private val classLoader: ClassLoader,
    private val filters: List<ClassDiscoveryFilter> = listOf(JRE_FILTER, GRADLE_FILTER),
    private val handler: (KClass<*>) -> Unit
) {
    suspend fun detect() {
        val classPath = classPath()
        if (log.isDebugEnabled) {
            log.debug("Class path:")
            classPath.forEach {
                log.debug("  Entry: $it")
            }
        }

        classPath.mapNotNull { file ->
            // Not allowed by any filter and file name is not hinting at success
            val normalized = file.absolutePath.replace(File.separatorChar, '.')
            if (filters.any { !it(file) } && packagesToLoadFrom.none { normalized.contains(it) }) {
                null
            } else {
                launch {
                    detectFile(file, file)
                }
            }
        }.joinAll()
    }

    private suspend fun detectFile(parent: File, file: File) {
        log.debug("detectFile($file)")
        if (file.isDirectory) {
            file.listFiles().map {
                launch {
                    detectFile(parent, it)
                }
            }.joinAll()
        } else {
            if (file.extension == "class") {
                val relativeFile = file.relativeTo(parent)
                loadFromName(relativeFile.path)
            } else if (file.extension == "jar") {
                val jarStream = JarInputStream(FileInputStream(file))
                while (true) {
                    val entry = jarStream.nextJarEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        loadFromName(entry.name)
                    }

                    jarStream.closeEntry()
                }
            }
        }
    }

    private fun normalizeName(fullPath: String): String? {
        if (!fullPath.endsWith(".class") || fullPath.contains('$')) return null
        return fullPath.replace(File.separatorChar, '.').replace(".class", "")
    }

    private fun loadFromName(name: String) {
        val className = normalizeName(name) ?: return
        if (packagesToLoadFrom.any { pkg -> className.startsWith(pkg) }) {
            try {
                val klass = classLoader.loadClass(className)
                if (klass.isKotlinClass()) {
                    handler(klass.kotlin)
                }
            } catch (ex: Exception) {
                log.debug("Caught an exception when loading $className")
                log.debug(ex.message)
            }
        }
    }

    private fun classPath(): List<File> {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator.toRegex())
            .dropLastWhile { it.isEmpty() }
            .map { File(it) }
    }

    companion object : Loggable {
        override val log = logger()

        val JRE_FILTER: ClassDiscoveryFilter = { file ->
            !((file.absolutePath.contains("java") || file.absolutePath.contains("jre")) && file.extension == "jar")
        }

        val GRADLE_FILTER: ClassDiscoveryFilter = { file ->
            !((file.absolutePath.contains(".gradle")) && file.extension == "jar")
        }
    }
}

typealias ClassDiscoveryFilter = (File) -> Boolean
