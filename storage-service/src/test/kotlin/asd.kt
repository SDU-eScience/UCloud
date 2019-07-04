import dk.sdu.cloud.file.services.FSResult
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.unwrap
import java.io.File
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.system.measureTimeMillis

fun main() {
    println(measureTimeMillis {
        println("1" + test("/etc", true).unwrap())
        println("2" + test("/etc", true).unwrap())
        println("3" + test("/etc/sudoers", true).unwrap())
        println("4" + test("/tmp", true).unwrap())
        println("5" + test("/tmp/mine.txt", false).unwrap())
        println("6" + test("/tmp/mine.txt", true).unwrap())
        println("7" + test("/etc", false).unwrap())
    })

}

fun test(path: String, requireWrite: Boolean): FSResult<Boolean> {
    val internalFile = File(path)
    val internalPath = internalFile.toPath()

    return try {
        val attributes = Files.readAttributes(internalPath, BasicFileAttributes::class.java)
        if (attributes.isDirectory) {
            if (requireWrite) {
                val resolve = internalPath.resolve("./.temporary_${UUID.randomUUID()}")
                println(resolve)
                val openOptions = hashSetOf<OpenOption>(
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.DELETE_ON_CLOSE
                )
                Files.newByteChannel(resolve, openOptions).close()
                Files.deleteIfExists(resolve)
            } else {
                internalPath.toFile().list()
            }
        } else if (attributes.isRegularFile) {
            val openOptions = hashSetOf<OpenOption>(StandardOpenOption.READ)
            if (requireWrite) openOptions += StandardOpenOption.WRITE

            Files.newByteChannel(internalPath, openOptions).close()
        } else {
            throw FSException.CriticalException("Invalid file type")
        }

        FSResult(0, true)
    } catch (ex: Throwable) {
        if (ex is FSException.CriticalException) {
            throw ex
        }

        FSResult(0, false)
    }
}
