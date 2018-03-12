package dk.sdu.cloud.app.services

sealed class SlurmEvent {
    abstract val jobId: Long
}

data class SlurmEventBegan(override val jobId: Long) : SlurmEvent()
data class SlurmEventEnded(override val jobId: Long) : SlurmEvent()
data class SlurmEventFailed(override val jobId: Long) : SlurmEvent()

typealias SlurmEventListener = (SlurmEvent) -> Unit

