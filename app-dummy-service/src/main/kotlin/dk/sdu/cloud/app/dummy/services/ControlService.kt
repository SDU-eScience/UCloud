package dk.sdu.cloud.app.dummy.services

import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.StateChangeRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.lang.Exception
import java.util.concurrent.atomic.AtomicInteger

class ControlService(
    private val client: AuthenticatedClient,
    private val println: (String) -> Unit
) {
    private var awaitingConfirmation = false
    private var answer: Boolean? = null
    private val counter = AtomicInteger(0)
    private val easyNames = HashMap<Int, String>()
    private val reverseEasyNames = HashMap<String, Int>()

    init {
        println("app-dummy service initialized.")
        println("Type commands below to trigger events.")
    }

    fun onInput(command: String) {
        if (awaitingConfirmation) {
            if (command == "y") {
                answer = true
            } else if (command == "n") {
                answer = false
            } else {
                println("Please type 'y' or 'n'")
            }
        } else {
            val tokens = command.split(" ")

            runBlocking {
                try {
                    when (tokens.first()) {
                        "state" -> {
                            val id = tokens[1].toInt()
                            val state = JobState.valueOf(tokens[2].toUpperCase())
                            val realId = easyNames.getValue(id)

                            ComputationCallbackDescriptions.requestStateChange.call(
                                StateChangeRequest(
                                    realId,
                                    state
                                ),
                                client
                            ).orThrow()
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    fun confirmAction(code: String, message: String) {
        synchronized(this) {
            awaitingConfirmation = true
            println("$message. Please confirm [y/n]:")

            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                val savedAnswer = answer
                if (savedAnswer != null) {
                    println("Confirmed: $savedAnswer")
                    awaitingConfirmation = false
                    answer = null

                    if (!savedAnswer) {
                        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Failing on purpose")
                    }

                    return
                }
                Thread.sleep(10)
            }

            println("Timeout!")
            awaitingConfirmation = false
            answer = null
        }
    }

    fun saveName(jobId: String): Int {
        if (jobId !in reverseEasyNames) {
            val newId = counter.getAndIncrement()
            reverseEasyNames[jobId] = newId
            easyNames[newId] = jobId
            return newId
        } else {
            return reverseEasyNames[jobId]!!
        }
    }
}