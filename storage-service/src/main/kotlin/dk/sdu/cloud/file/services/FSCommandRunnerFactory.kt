package dk.sdu.cloud.file.services

import kotlinx.coroutines.runBlocking
import java.io.Closeable
import kotlin.reflect.KClass

abstract class FSCommandRunnerFactory<Ctx : FSUserContext> {
    abstract val type: KClass<Ctx>
    abstract operator fun invoke(user: String): Ctx
}

inline fun <Ctx : FSUserContext, R> FSCommandRunnerFactory<Ctx>.withContext(user: String, consumer: (Ctx) -> R): R {
    return invoke(user).use(consumer)
}

fun <Ctx : FSUserContext, R> FSCommandRunnerFactory<Ctx>.withBlockingContext(
    user: String,
    consumer: suspend (Ctx) -> R
): R {
    return invoke(user).use {
        runBlocking {
            consumer(it)
        }
    }
}

interface CommandRunner : Closeable {
    val user: String
}

typealias FSUserContext = CommandRunner
