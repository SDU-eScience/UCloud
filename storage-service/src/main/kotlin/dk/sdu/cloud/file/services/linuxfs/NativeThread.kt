package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Platform
import java.nio.file.Files

// https://medium.com/@unmeshvjoshi/how-java-thread-maps-to-os-thread-e280a9fb2e06

class NativeThread(private val name: String, private val block: () -> Unit) {
    fun run() {
        Thread.currentThread().name = name
        block()
    }

    fun start() {
        if (!disableNativeThreads) {
            start0()
        } else {
            Thread {
                this@NativeThread.run()
            }.start()
        }
    }

    private external fun start0()

    companion object {
        init {
            val tempFile = Files.createTempFile("libthreading", ".so").toFile()
            val resourceStream = if (Platform.isMac()) {
                LinuxFS::class.java.classLoader.getResourceAsStream("libthreading.dylib")
            } else {
                LinuxFS::class.java.classLoader.getResourceAsStream("libthreading.so")
            }

            tempFile.outputStream().use { out ->
                resourceStream.use { it.copyTo(out) }
            }

            tempFile.deleteOnExit()
            System.load(tempFile.absolutePath)
        }

        var disableNativeThreads = false
    }
}
