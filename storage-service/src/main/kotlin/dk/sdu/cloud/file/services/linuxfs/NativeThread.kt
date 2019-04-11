package dk.sdu.cloud.file.services.linuxfs

import java.util.concurrent.atomic.AtomicInteger

// TODO We need to be sure we get a native thread! https://medium.com/@unmeshvjoshi/how-java-thread-maps-to-os-thread-e280a9fb2e06

class NativeThread(private val block: () -> Unit) {
    init {
        val toString = LinuxFS::class.java.classLoader.getResource("libthreading.dylib").toURI().path
        System.load(toString)
    }

    var name: String
        get() = Thread.currentThread().name
        set(value) {
            Thread.currentThread().name = value
        }

    fun run() {
        println("Running Thread " + threadId.getAndIncrement())
        block()
    }

    fun start() {
        start0()
    }

    private external fun start0()

    companion object {
        val threadId = AtomicInteger(1)
    }
}
