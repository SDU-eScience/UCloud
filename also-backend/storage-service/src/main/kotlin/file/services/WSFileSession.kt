package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.WSSession
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A service for handling websocket sessions
 *
 * Each websocket session has an associated [CommandRunner].
 */
class WSFileSessionService<Ctx : FSUserContext>(
    private val factory: FSCommandRunnerFactory<Ctx>
) {
    private val sessions = HashMap<Pair<String, WSSession>, WSFileSession<Ctx>>()
    private val mutex = Mutex()

    suspend fun submitJob(call: WSCall, user: String, job: WSFileSystemJob<Ctx>) {
        val session: WSFileSession<Ctx> = mutex.withLock {
            val userAndSession = Pair(user, call.session)
            val existingSession = sessions[userAndSession]
            if (existingSession != null) return@withLock existingSession

            val session = WSFileSession(factory = { factory(user) })
            sessions[userAndSession] = session

            call.session.addOnCloseHandler {
                session.internalClose()

                mutex.withLock {
                    sessions.remove(userAndSession)
                }
            }

            return@withLock session
        }

        coroutineScope {
            session.processCommandsAsync(this, job).join()
        }
    }
}

typealias WSFileSystemJob<Ctx> = suspend (Ctx) -> Unit

class WSFileSession<Ctx : FSUserContext>(
    private val factory: suspend () -> Ctx
) {
    private var currentCommandRunner: Ctx? = null
    private val mutex = Mutex()

    fun processCommandsAsync(scope: CoroutineScope, job: WSFileSystemJob<Ctx>): Job {
        return scope.launch {
            job(retrieveCommandRunner())
        }
    }

    private suspend fun retrieveCommandRunner(): Ctx {
        mutex.withLock {
            val capturedRunner = currentCommandRunner
            if (capturedRunner != null) {
                log.debug("Using existing command runner")
                return capturedRunner
            }

            log.debug("Creating new command runner")
            val ctx = factory()
            currentCommandRunner = ctx
            return ctx
        }
    }

    internal fun internalClose() {
        currentCommandRunner?.close()
    }

    companion object : Loggable {
        override val log = logger()
    }
}

