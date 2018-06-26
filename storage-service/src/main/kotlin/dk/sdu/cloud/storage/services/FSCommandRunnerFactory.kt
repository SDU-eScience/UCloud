package dk.sdu.cloud.storage.services

import java.io.Closeable

abstract class FSCommandRunnerFactory<Ctx : FSUserContext> {
    abstract operator fun invoke(user: String): Ctx

    inline fun <R> withContext(user: String, consumer: (Ctx) -> R): R {
        return invoke(user).use(consumer)
    }
}

interface CommandRunner : Closeable {
    val user: String
}

typealias FSUserContext = CommandRunner
