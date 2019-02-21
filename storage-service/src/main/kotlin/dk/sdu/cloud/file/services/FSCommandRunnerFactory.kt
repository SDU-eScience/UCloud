package dk.sdu.cloud.file.services

import kotlinx.coroutines.runBlocking
import java.io.Closeable
import kotlin.reflect.KClass

abstract class FSCommandRunnerFactory<Ctx : FSUserContext> {
    abstract val type: KClass<Ctx>
    abstract suspend operator fun invoke(user: String): Ctx
}

suspend inline fun <Ctx : FSUserContext, R> FSCommandRunnerFactory<Ctx>.withContext(
    user: String,
    consumer: (Ctx) -> R
): R {
    return invoke(user).use(consumer)
}

fun <Ctx : FSUserContext, R> FSCommandRunnerFactory<Ctx>.withBlockingContext(
    user: String,
    consumer: suspend (Ctx) -> R
): R {
    return runBlocking {
        invoke(user).use {
            consumer(it)
        }
    }
}

interface CommandRunner : Closeable {
    val user: String
}

typealias FSUserContext = CommandRunner

sealed class FSCommandRunnerException(why: String) : RuntimeException(why) {
    class DeadChannel : FSCommandRunnerException("Dead channel")
}
